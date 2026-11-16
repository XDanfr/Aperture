package me.xdan.aperture.ui.screen.jellyfin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.xdan.aperture.data.jellyfin.JellyfinAccounts
import me.xdan.aperture.data.jellyfin.JellyfinLibraryWatcher
import me.xdan.aperture.data.local.dao.JellyfinDao
import me.xdan.aperture.data.local.entity.JellyfinAccountEntity
import me.xdan.aperture.domain.model.LibraryMode
import me.xdan.aperture.domain.repository.ProfileRepository

/** Settings › Jellyfin: signing the active profile in and choosing how its library is built. */
class JellyfinViewModel(
    private val accounts: JellyfinAccounts,
    private val profiles: ProfileRepository,
    private val watcher: JellyfinLibraryWatcher,
    private val dao: JellyfinDao,
    private val platform: me.xdan.aperture.companion.CompanionPlatform
) : ViewModel() {

    sealed interface Setup {
        /** Android 17 needs local network access before a LAN server can be found or reached. */
        data object LocalNetworkPermission : Setup
        /** Looking for servers; [servers] fills in as they answer. */
        data class FindServer(
            val servers: List<JellyfinAccounts.DiscoveredServer> = emptyList(),
            val searching: Boolean = true,
            val error: String? = null
        ) : Setup
        data class Connecting(val address: String) : Setup
        data class QuickConnect(val address: String, val serverName: String, val code: String) : Setup
        data class Password(val address: String, val serverName: String, val error: String? = null, val busy: Boolean = false) : Setup
        /** Signed in: how should this profile's library work? */
        data class ChooseMode(val account: JellyfinAccountEntity) : Setup
    }

    val account: StateFlow<JellyfinAccountEntity?> = accounts.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val libraryMode: StateFlow<LibraryMode> = profiles.activeLibraryMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryMode.LOCAL)

    val syncState: StateFlow<JellyfinLibraryWatcher.State> = watcher.state

    /** Titles indexed for the active account: movies plus shows. */
    val indexedTitles: StateFlow<Int> = accounts.activeAccount
        .flatMapLatest { account ->
            if (account == null) flowOf(0) else dao.titleCount(account.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _setup = MutableStateFlow<Setup?>(null)
    /** Null when not signing in. */
    val setup: StateFlow<Setup?> = _setup.asStateFlow()

    private var setupJob: Job? = null

    fun startSetup() {
        setupJob?.cancel()
        if (!platform.hasLocalNetworkPermission) {
            _setup.value = Setup.LocalNetworkPermission
            return
        }
        findServers()
    }

    /** After the permission prompt; servers outside the LAN still work without it. */
    fun onLocalNetworkResult(granted: Boolean) {
        if (granted) findServers() else _setup.value = Setup.FindServer(
            searching = false,
            error = "Without local network access, only servers reached over the internet will work."
        )
    }

    /** Skips the permission and goes straight to typing an address. */
    fun enterAddressInstead() {
        setupJob?.cancel()
        _setup.value = Setup.FindServer(searching = false)
    }

    private fun findServers() {
        setupJob?.cancel()
        _setup.value = Setup.FindServer()
        setupJob = viewModelScope.launch {
            val found = mutableListOf<JellyfinAccounts.DiscoveredServer>()
            kotlinx.coroutines.withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
                accounts.discoverServers().collect { server ->
                    if (found.none { it.id == server.id }) {
                        found += server
                        (_setup.value as? Setup.FindServer)?.let { _setup.value = it.copy(servers = found.toList()) }
                    }
                }
            }
            (_setup.value as? Setup.FindServer)?.let { _setup.value = it.copy(searching = false) }
        }
    }

    /** Connects to [address] and starts Quick Connect, falling back to a password when it's off. */
    fun connect(address: String) {
        if (address.isBlank()) return
        setupJob?.cancel()
        _setup.value = Setup.Connecting(address)
        setupJob = viewModelScope.launch {
            val profileId = profiles.activeProfileId.first()
            val session = try {
                accounts.startQuickConnect(profileId, address)
            } catch (e: CancellationException) {
                throw e
            } catch (e: JellyfinAccounts.SignInError.QuickConnectDisabled) {
                val info = runCatching { accounts.probe(address, profileId) }.getOrNull()
                _setup.value = Setup.Password(JellyfinAccounts.normalise(address), info?.serverName ?: "Jellyfin")
                return@launch
            } catch (e: JellyfinAccounts.SignInError) {
                _setup.value = Setup.FindServer(searching = false, error = e.message)
                return@launch
            }
            _setup.value = Setup.QuickConnect(session.baseUrl, session.serverName, session.code)
            try {
                val account = accounts.awaitQuickConnect(session)
                finishSignIn(account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _setup.value = Setup.FindServer(searching = false, error = e.message ?: "Quick Connect didn't finish")
            }
        }
    }

    fun usePassword() {
        val current = _setup.value as? Setup.QuickConnect ?: return
        setupJob?.cancel()
        _setup.value = Setup.Password(current.address, current.serverName)
    }

    fun signInWithPassword(username: String, password: String) {
        val current = _setup.value as? Setup.Password ?: return
        if (username.isBlank()) return
        setupJob?.cancel()
        _setup.value = current.copy(busy = true, error = null)
        setupJob = viewModelScope.launch {
            try {
                val account = accounts.signInWithPassword(profiles.activeProfileId.first(), current.address, username, password)
                finishSignIn(account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _setup.value = current.copy(busy = false, error = e.message ?: "Couldn't sign in")
            }
        }
    }

    private suspend fun finishSignIn(account: JellyfinAccountEntity) {
        _setup.value = Setup.ChooseMode(account)
    }

    fun chooseMode(mode: LibraryMode) {
        viewModelScope.launch {
            profiles.setLibraryMode(profiles.activeProfileId.first(), mode)
            _setup.value = null
        }
    }

    fun setLibraryMode(mode: LibraryMode) {
        viewModelScope.launch { profiles.setLibraryMode(profiles.activeProfileId.first(), mode) }
    }

    fun cancelSetup() {
        setupJob?.cancel()
        _setup.value = null
    }

    fun syncNow() {
        val current = account.value ?: return
        viewModelScope.launch { watcher.refresh(current) }
    }

    fun signOut() {
        viewModelScope.launch {
            val profileId = profiles.activeProfileId.first()
            accounts.signOut(profileId)
            profiles.setLibraryMode(profileId, LibraryMode.LOCAL)
        }
    }

    private companion object {
        const val DISCOVERY_TIMEOUT_MS = 6_000L
    }
}
