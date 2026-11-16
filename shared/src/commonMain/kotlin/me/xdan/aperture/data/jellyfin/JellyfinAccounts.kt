package me.xdan.aperture.data.jellyfin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import me.xdan.aperture.data.local.dao.JellyfinDao
import me.xdan.aperture.data.local.entity.JellyfinAccountEntity
import me.xdan.aperture.domain.repository.ProfileRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticationApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo

/** Signing profiles in and out of Jellyfin. One account per profile. */
class JellyfinAccounts(
    private val dao: JellyfinDao,
    private val clients: JellyfinClients,
    private val credentials: JellyfinCredentials,
    profiles: ProfileRepository
) {
    sealed class SignInError(message: String) : Exception(message) {
        class Unreachable(cause: Throwable) : SignInError("Couldn't reach the server") { init { initCause(cause) } }
        class NotJellyfin : SignInError("That address isn't a Jellyfin server")
        class TooOld(val version: String) : SignInError("Jellyfin $version is too old. Aperture needs Jellyfin 12.0 or newer.")
        class Rejected : SignInError("The username or password is wrong")
        class QuickConnectDisabled : SignInError("Quick Connect is turned off on this server")
        class QuickConnectExpired : SignInError("The code expired. Try again for a new one.")
    }

    /** A server found on the local network. */
    data class DiscoveredServer(val id: String, val name: String, val address: String)

    /** A Quick Connect code waiting to be approved from another device. */
    data class QuickConnectSession(
        val profileId: Long,
        val baseUrl: String,
        val serverName: String,
        val code: String,
        internal val secret: String,
        internal val info: PublicSystemInfo
    )

    /** Jellyfin servers answering the local network broadcast, as they reply. */
    fun discoverServers(): Flow<DiscoveredServer> = JellyfinDiscovery.discover()
        .catch { me.xdan.aperture.platform.Log.w("JellyfinAccounts", "Discovery failed: ${it.message}") }

    suspend fun startQuickConnect(profileId: Long, baseUrl: String): QuickConnectSession = withContext(Dispatchers.IO) {
        val url = normalise(baseUrl)
        val info = probe(url, profileId)
        val api = clients.anonymous(url, profileId)
        val enabled = runCatching { api.authenticationApi.getQuickConnectEnabled().content }.getOrDefault(false)
        if (!enabled) throw SignInError.QuickConnectDisabled()
        val result = try {
            api.authenticationApi.initiateQuickConnect().content
        } catch (e: org.jellyfin.sdk.api.client.exception.InvalidStatusException) {
            if (e.status == 401 || e.status == 403) throw SignInError.QuickConnectDisabled() else throw SignInError.Unreachable(e)
        }
        QuickConnectSession(profileId, url, info.serverName ?: "Jellyfin", result.code, result.secret, info)
    }

    /**
     * Waits until the code is approved, then signs in. Cancel the coroutine to give up; a code the
     * server has forgotten (they expire after a few minutes) throws [SignInError.QuickConnectExpired].
     */
    suspend fun awaitQuickConnect(session: QuickConnectSession, pollMs: Long = 3_000): JellyfinAccountEntity =
        withContext(Dispatchers.IO) {
            val api = clients.anonymous(session.baseUrl, session.profileId)
            while (true) {
                val state = try {
                    api.authenticationApi.getQuickConnectState(session.secret).content
                } catch (e: org.jellyfin.sdk.api.client.exception.InvalidStatusException) {
                    if (e.status == 404) throw SignInError.QuickConnectExpired() else throw SignInError.Unreachable(e)
                }
                if (state.authenticated) break
                kotlinx.coroutines.delay(pollMs)
            }
            val result = api.authenticationApi
                .authenticateWithQuickConnect(org.jellyfin.sdk.model.api.QuickConnectDto(secret = session.secret))
                .content
            complete(session.profileId, session.baseUrl, session.info, result)
        }

    val activeAccount: Flow<JellyfinAccountEntity?> = profiles.activeProfileId.flatMapLatest(dao::accountForProfile)

    fun accountFor(profileId: Long): Flow<JellyfinAccountEntity?> = dao.accountForProfile(profileId)

    suspend fun accountForOnce(profileId: Long): JellyfinAccountEntity? = dao.accountForProfileOnce(profileId)

    /** Checks [baseUrl] is a Jellyfin 12+ server. */
    suspend fun probe(baseUrl: String, profileId: Long): PublicSystemInfo = withContext(Dispatchers.IO) {
        val info = try {
            clients.anonymous(normalise(baseUrl), profileId).systemApi.getPublicSystemInfo().content
        } catch (e: Exception) {
            throw SignInError.Unreachable(e)
        }
        if (info.id.isNullOrBlank()) throw SignInError.NotJellyfin()
        val version = info.version.orEmpty()
        if (!isSupported(version)) throw SignInError.TooOld(version)
        info
    }

    suspend fun signInWithPassword(profileId: Long, baseUrl: String, username: String, password: String): JellyfinAccountEntity =
        withContext(Dispatchers.IO) {
            val url = normalise(baseUrl)
            val info = probe(url, profileId)
            val api = clients.anonymous(url, profileId)
            val result = try {
                api.authenticationApi.authenticateUserByName(AuthenticateUserByName(username = username, pw = password)).content
            } catch (e: org.jellyfin.sdk.api.client.exception.InvalidStatusException) {
                if (e.status == 401) throw SignInError.Rejected() else throw SignInError.Unreachable(e)
            }
            complete(profileId, url, info, result)
        }

    /** Stores a successful sign-in, replacing whatever account the profile had. */
    internal suspend fun complete(
        profileId: Long,
        baseUrl: String,
        info: PublicSystemInfo,
        result: AuthenticationResult
    ): JellyfinAccountEntity {
        val token = requireNotNull(result.accessToken) { "The server didn't return an access token" }
        val user = requireNotNull(result.user) { "The server didn't return the user" }
        dao.accountForProfileOnce(profileId)?.let { forget(it) }
        val account = JellyfinAccountEntity(
            profileId = profileId,
            serverId = result.serverId ?: info.id.orEmpty(),
            serverName = info.serverName ?: "Jellyfin",
            baseUrl = baseUrl,
            serverVersion = info.version,
            userId = JellyfinIds.of(user.id),
            userName = user.name ?: "",
            isAdministrator = user.policy?.isAdministrator == true
        )
        val id = dao.insertAccount(account)
        credentials.saveToken(id, token)
        return account.copy(id = id)
    }

    /** Ends the server session too, so the device disappears from the user's Jellyfin dashboard. */
    suspend fun signOut(profileId: Long) = withContext(Dispatchers.IO) {
        val account = dao.accountForProfileOnce(profileId) ?: return@withContext
        clients.forAccount(account)?.let { api -> runCatching { api.sessionApi.reportSessionEnded() } }
        forget(account)
    }

    /** Called when a profile is deleted: no server call, just local cleanup. */
    suspend fun forgetProfile(profileId: Long) {
        dao.accountForProfileOnce(profileId)?.let { forget(it) }
    }

    fun api(account: JellyfinAccountEntity): ApiClient? = clients.forAccount(account)

    private suspend fun forget(account: JellyfinAccountEntity) {
        dao.deleteAccount(account.id)
        credentials.forget(account.id)
        clients.forget(account.id)
    }

    companion object {
        const val MINIMUM_MAJOR_VERSION = 12

        fun isSupported(version: String): Boolean =
            version.substringBefore('.').toIntOrNull()?.let { it >= MINIMUM_MAJOR_VERSION } == true

        /** Accepts "host", "host:port" or a full URL; trims slashes and a trailing /web. */
        fun normalise(input: String): String {
            var url = input.trim().trimEnd('/')
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "http://$url"
            }
            if (url.endsWith("/web", ignoreCase = true)) url = url.dropLast(4)
            return url.trimEnd('/')
        }
    }
}
