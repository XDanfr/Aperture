package me.xdan.aperture.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.LibraryPreparationStage
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import me.xdan.aperture.data.update.UpdateCheckState
import me.xdan.aperture.data.update.UpdateManager

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaRepository: MediaRepository,
    private val updateManager: UpdateManager,
    private val artworkPalettes: me.xdan.aperture.data.artwork.ArtworkPaletteStore,
    private val legacyMigration: me.xdan.aperture.data.update.LegacyInstallMigration
) : ViewModel() {

    private var preparationJob: Job? = null
    private var hasStartedPreparation = false

    // Using Boolean? where null means "loading"
    val isOnboardingCompleted: StateFlow<Boolean?> = userPreferencesRepository.isOnboardingCompleted
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val libraryPreparation = mediaRepository.preparationProgress

    val themeId = userPreferencesRepository.themeId.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "purple"
    )

    private val _dynamicAccentArgb = MutableStateFlow<Int?>(null)
    val dynamicAccentArgb: StateFlow<Int?> = _dynamicAccentArgb
    private val activeMediaId = MutableStateFlow<Long?>(null)

    val isTutorialRequired = userPreferencesRepository.isTutorialRequired.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    val tutorialExampleMedia = mediaRepository.getAllMedia()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val updateState = updateManager.state
    val legacyCleanupRequired = legacyMigration.cleanupRequired

    fun refreshLegacyInstall() = legacyMigration.refresh()

    fun setTheme(themeId: String) {
        viewModelScope.launch { userPreferencesRepository.setThemeId(themeId) }
    }

    init {
        viewModelScope.launch {
            me.xdan.aperture.ui.artwork.dynamicArtworkSeeds(
                activeMediaId, mediaRepository.getAllMedia(), userPreferencesRepository.themeId,
                artworkPalettes::observeSeed
            ).collect { _dynamicAccentArgb.value = it }
        }
        viewModelScope.launch {
            userPreferencesRepository.isOnboardingCompleted
                .filter { it }
                .first()
            startLibraryPreparation()
        }
        viewModelScope.launch {
            combine(
                userPreferencesRepository.isOnboardingCompleted,
                userPreferencesRepository.isTutorialRequired
            ) { onboardingCompleted, tutorialRequired ->
                onboardingCompleted && !tutorialRequired
            }
                .filter { it }
                .first()
            updateManager.checkForUpdates(silent = true)
        }
    }

    fun startLibraryPreparation(force: Boolean = false) {
        if (preparationJob?.isActive == true) return
        if (!force &&
            hasStartedPreparation &&
            mediaRepository.preparationProgress.value.stage != LibraryPreparationStage.ERROR
        ) return
        hasStartedPreparation = true
        preparationJob = viewModelScope.launch {
            mediaRepository.scanLocalFiles()
        }
    }

    fun completeOnboarding(showTutorial: Boolean = false) {
        viewModelScope.launch {
            userPreferencesRepository.setTutorialRequired(showTutorial && !legacyMigration.wasLegacyInstallDetected)
            userPreferencesRepository.setOnboardingCompleted(true)
        }
    }

    fun completeTutorial() {
        viewModelScope.launch { userPreferencesRepository.setTutorialRequired(false) }
    }

    fun downloadAndInstallUpdate(update: UpdateCheckState.Available) {
        viewModelScope.launch { updateManager.downloadAndInstall(update) }
    }

    fun setActiveMedia(mediaId: Long) {
        activeMediaId.value = mediaId
    }

}
