package me.xdan.aperture.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.annotation.ExperimentalCoilApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.xdan.aperture.data.update.UpdateCheckState
import me.xdan.aperture.data.update.UpdateManager
import me.xdan.aperture.data.subtitles.OpenSubtitlesSessionManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val updateManager: UpdateManager,
    private val openSubtitlesSessionManager: OpenSubtitlesSessionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val spotlightSettings: StateFlow<SpotlightSettings> = combine(
        userPreferencesRepository.hideFinishedFromSpotlight,
        userPreferencesRepository.finishedSpotlightExclusionDays,
        userPreferencesRepository.roundedSpotlight
    ) { hideFinished, exclusionDays, roundedSpotlight ->
        SpotlightSettings(hideFinished, exclusionDays, roundedSpotlight)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SpotlightSettings()
    )

    val themeId = userPreferencesRepository.themeId.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "purple"
    )
    val showPresentationMode = userPreferencesRepository.showPresentationMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "grouped"
    )
    val subtitleAppearance = combine(
        userPreferencesRepository.subtitleTextScale,
        userPreferencesRepository.subtitleColour,
        userPreferencesRepository.subtitleBackgroundOpacity
    ) { scale, colour, backgroundOpacity ->
        SubtitleAppearanceSettings(scale, colour, backgroundOpacity)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SubtitleAppearanceSettings()
    )
    val updateState = updateManager.state
    val openSubtitlesSession = openSubtitlesSessionManager.state
    val hiddenMedia = repository.getHiddenMedia().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val mediaFolders = repository.mediaFolders
    val mediaFolderMessage = MutableStateFlow<String?>(null)

    fun forceRescan() {
        viewModelScope.launch {
            repository.scanLocalFiles()
        }
    }

    fun addMediaFolder(uri: Uri) {
        viewModelScope.launch {
            mediaFolderMessage.value = "Adding folder…"
            repository.addMediaFolder(uri.toString())
                .onSuccess {
                    mediaFolderMessage.value = "Folder added. Scanning now…"
                    repository.scanLocalFiles()
                    mediaFolderMessage.value = null
                }
                .onFailure { error ->
                    mediaFolderMessage.value = error.message ?: "Aperture could not add that folder"
                }
        }
    }

    fun removeMediaFolder(uri: String) {
        viewModelScope.launch {
            repository.removeMediaFolder(uri)
            mediaFolderMessage.value = null
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache() {
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
    }

    fun setHideFinishedFromSpotlight(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHideFinishedFromSpotlight(enabled)
        }
    }

    fun setFinishedSpotlightExclusionDays(days: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setFinishedSpotlightExclusionDays(days)
        }
    }

    fun setRoundedSpotlight(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setRoundedSpotlight(enabled) }
    }

    fun setTheme(themeId: String) {
        viewModelScope.launch { userPreferencesRepository.setThemeId(themeId) }
    }

    fun setShowPresentationMode(mode: String) {
        viewModelScope.launch { userPreferencesRepository.setShowPresentationMode(mode) }
    }

    fun setSubtitleAppearance(settings: SubtitleAppearanceSettings) {
        viewModelScope.launch {
            userPreferencesRepository.setSubtitleTextScale(settings.textScale)
            userPreferencesRepository.setSubtitleColour(settings.colour)
            userPreferencesRepository.setSubtitleBackgroundOpacity(settings.backgroundOpacity)
        }
    }

    fun unhide(mediaId: Long) {
        viewModelScope.launch { repository.setHidden(mediaId, false) }
    }

    fun checkForUpdates() {
        viewModelScope.launch { updateManager.checkForUpdates() }
    }

    fun downloadAndInstall(update: UpdateCheckState.Available) {
        viewModelScope.launch { updateManager.downloadAndInstall(update) }
    }

    fun resumeUpdateAfterPermission(update: UpdateCheckState.Available) {
        downloadAndInstall(update)
    }

    fun loginToOpenSubtitles(username: String, password: String) {
        viewModelScope.launch { openSubtitlesSessionManager.login(username, password) }
    }

    fun logoutOfOpenSubtitles() {
        openSubtitlesSessionManager.logout()
    }

}

data class SubtitleAppearanceSettings(
    val textScale: Float = 1f,
    val colour: String = "white",
    val backgroundOpacity: Float = 0.55f
)

data class SpotlightSettings(
    val hideFinishedFromSpotlight: Boolean = true,
    val exclusionDays: Int = 14,
    val roundedSpotlight: Boolean = false
)
