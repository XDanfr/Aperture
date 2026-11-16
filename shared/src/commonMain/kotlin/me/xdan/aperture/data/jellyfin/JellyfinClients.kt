package me.xdan.aperture.data.jellyfin

import me.xdan.aperture.companion.CompanionPlatform
import me.xdan.aperture.data.local.entity.JellyfinAccountEntity
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.DeviceInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds SDK clients. Each platform provides the [Jellyfin] instance (Android needs a Context);
 * this class adds the per-profile device identity and caches one client per signed-in account.
 */
class JellyfinClients(
    val jellyfin: Jellyfin,
    private val credentials: JellyfinCredentials,
    private val platform: CompanionPlatform
) {
    private val clients = ConcurrentHashMap<Long, ApiClient>()

    fun deviceInfo(profileId: Long) = DeviceInfo(
        id = credentials.deviceId(profileId),
        name = platform.deviceName
    )

    /** A client for signing in or reading public server info. */
    fun anonymous(baseUrl: String, profileId: Long): ApiClient =
        jellyfin.createApi(baseUrl = baseUrl, deviceInfo = deviceInfo(profileId))

    /** The signed-in client for [account], or null when its token is gone (e.g. after a restore). */
    fun forAccount(account: JellyfinAccountEntity): ApiClient? {
        val token = credentials.token(account.id) ?: return null
        val cached = clients[account.id]
        if (cached != null && cached.accessToken == token && cached.baseUrl == account.baseUrl) return cached
        return jellyfin.createApi(
            baseUrl = account.baseUrl,
            accessToken = token,
            deviceInfo = deviceInfo(account.profileId)
        ).also { clients[account.id] = it }
    }

    fun forget(accountId: Long) {
        clients.remove(accountId)
    }
}
