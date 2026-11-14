package me.xdan.aperture.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.xdan.aperture.companion.protocol.CompanionCommand
import me.xdan.aperture.companion.protocol.CompanionProtocol
import me.xdan.aperture.companion.protocol.PairingInvite
import me.xdan.aperture.mobile.CompanionApplication
import me.xdan.aperture.mobile.data.DiscoveredTv
import me.xdan.aperture.mobile.data.Library
import me.xdan.aperture.mobile.data.PairingResult
import me.xdan.aperture.mobile.ui.theme.resolvePaletteId

class CompanionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CompanionApplication
    private val settings = app.settings
    val deviceId: String get() = settings.deviceId
    private val repository = app.repository

    val connection = repository.connection
    val nowPlaying = repository.nowPlaying
    val tvState = repository.tvState
    val pairedTv = settings.pairedTv
    val themeChoice = settings.themeChoice
    val onboardingDone = settings.onboardingDone

    val library: StateFlow<Library?> = repository.library
        .map { items -> items?.let(::Library) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** palette id after resolving Sync with TV (Artwork Dynamic → Aperture Purple). */
    val paletteId: StateFlow<String> = combine(settings.themeChoice, settings.lastTvTheme) { choice, tvTheme ->
        resolvePaletteId(choice, tvTheme)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, resolvePaletteId(settings.themeChoice.value, settings.lastTvTheme.value))

    fun discoverTvs() = app.discovery.discover()

    fun parseInvite(raw: String): PairingInvite? = CompanionProtocol.parsePairingUri(raw)

    suspend fun pairWithInvite(invite: PairingInvite): PairingResult = repository.pairWithInvite(invite)
    suspend fun requestPairing(tv: DiscoveredTv): PairingResult = repository.requestPairing(tv)
    suspend fun pairWithCode(host: String, port: Int, code: String, tvId: String = "", tvName: String = ""): PairingResult =
        repository.pair(host, port, code, tvId, tvName)

    fun send(command: CompanionCommand) {
        viewModelScope.launch { repository.send(command) }
    }

    fun playOnTv(mediaId: Long, fromStart: Boolean = false) = send(CompanionCommand.PlayMedia(mediaId, fromStart))

    fun setFavourite(mediaId: Long, favourite: Boolean) = send(CompanionCommand.SetFavorite(mediaId, favourite))

    fun setThemeChoice(choice: String) = settings.setThemeChoice(choice)

    fun completeOnboarding() = settings.setOnboardingDone(true)

    fun reconnect() = repository.reconnectNow()

    fun forgetTv() {
        viewModelScope.launch { repository.forgetTv() }
    }

    fun onForeground() = repository.onForeground()
    fun onBackground() = repository.onBackground()
}
