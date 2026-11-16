package me.xdan.aperture.data.jellyfin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.xdan.aperture.data.local.dao.JellyfinDao
import me.xdan.aperture.data.local.entity.JellyfinAccountEntity
import me.xdan.aperture.domain.repository.ProfileRepository
import me.xdan.aperture.platform.Log
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage

/**
 * Keeps the active profile's index current while the app runs: a sync when the profile (or its
 * account) becomes active, then WebSocket changes as they happen and an incremental sync every
 * [INCREMENTAL_INTERVAL_MS] to cover a dropped socket.
 */
class JellyfinLibraryWatcher(
    private val accounts: JellyfinAccounts,
    private val profiles: ProfileRepository,
    private val sync: JellyfinSync,
    private val dao: JellyfinDao
) {
    sealed interface State {
        data object Idle : State
        data object Syncing : State
        data class Synced(val at: Long) : State
        /** The token is gone or was revoked; the profile needs to sign in again. */
        data object SignedOut : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Runs until cancelled; the app calls it from its top-level composition. */
    suspend fun run() {
        combine(accounts.activeAccount, profiles.activeLibraryMode) { account, mode ->
            account?.takeIf { mode.usesJellyfin }
        }
            .distinctUntilChanged { old, new -> old?.id == new?.id }
            .collectLatest { account ->
                if (account == null) {
                    _state.value = State.Idle
                    return@collectLatest
                }
                watch(account)
            }
    }

    /** A sync now, e.g. from a "Refresh" action. */
    suspend fun refresh(account: JellyfinAccountEntity) {
        syncOnce(dao.account(account.id) ?: return)
    }

    private suspend fun watch(account: JellyfinAccountEntity) = coroutineScope {
        if (!syncOnce(account)) return@coroutineScope
        val api = accounts.api(account) ?: return@coroutineScope
        launch {
            runCatching {
                api.webSocket.subscribe<LibraryChangedMessage>().collect { message ->
                    message.data?.let { runCatching { sync.applyLibraryChange(currentOf(account), it) } }
                }
            }.onFailure { if (it is CancellationException) throw it else Log.w(TAG, "Library socket ended: ${it.message}") }
        }
        launch {
            runCatching {
                api.webSocket.subscribe<UserDataChangedMessage>().collect { message ->
                    message.data?.let { runCatching { sync.applyUserDataChange(currentOf(account), it) } }
                }
            }.onFailure { if (it is CancellationException) throw it else Log.w(TAG, "User data socket ended: ${it.message}") }
        }
        while (true) {
            delay(INCREMENTAL_INTERVAL_MS)
            syncOnce(currentOf(account))
        }
    }

    /** Returns false when the account can't sync at all (signed out). */
    private suspend fun syncOnce(account: JellyfinAccountEntity): Boolean {
        _state.value = State.Syncing
        return try {
            sync.sync(account)
            _state.value = State.Synced(System.currentTimeMillis())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: JellyfinSync.NotSignedIn) {
            _state.value = State.SignedOut
            false
        } catch (e: org.jellyfin.sdk.api.client.exception.InvalidStatusException) {
            _state.value = if (e.status == 401) State.SignedOut else State.Failed("The server answered ${e.status}")
            e.status != 401
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed: ${e.message}")
            _state.value = State.Failed(e.message ?: "Couldn't reach the server")
            true
        }
    }

    private suspend fun currentOf(account: JellyfinAccountEntity) = dao.account(account.id) ?: account

    private companion object {
        const val TAG = "JellyfinWatcher"
        const val INCREMENTAL_INTERVAL_MS = 15 * 60 * 1000L
    }
}
