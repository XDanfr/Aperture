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
import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.data.update.UpdateCheckState
import me.xdan.aperture.data.update.UpdateManager
import me.xdan.aperture.ui.artwork.extractArtworkAccent

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaRepository: MediaRepository,
    private val updateManager: UpdateManager,
    @ApplicationContext private val context: Context
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
    private val artworkAccentCache = mutableMapOf<Long, Int>()
    private var requestedActiveMediaId: Long? = null
    private var dynamicThemeJob: Job? = null

    val isTutorialRequired = userPreferencesRepository.isTutorialRequired.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    val tutorialExampleMedia = mediaRepository.getAllMedia()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val updateState = updateManager.state

    init {
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
            userPreferencesRepository.setTutorialRequired(showTutorial)
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
        if (themeId.value != "dynamic") return
        requestedActiveMediaId = mediaId
        dynamicThemeJob?.cancel()
        artworkAccentCache[mediaId]?.let {
            _dynamicAccentArgb.value = it
            return
        }
        dynamicThemeJob = viewModelScope.launch {
            // Avoid recolouring the whole app while the user is rapidly
            // traversing a row; settle briefly on the focused card first.
            kotlinx.coroutines.delay(140)
            if (requestedActiveMediaId != mediaId) return@launch
            val media = mediaRepository.getMediaById(mediaId) ?: return@launch
            val artworkPath = media.backdropPath ?: media.posterPath ?: return@launch
            val url = TmdbApi.IMAGE_BASE_URL + "w780" + artworkPath
            val accent = withContext(Dispatchers.IO) {
                runCatching {
                    val result = context.imageLoader.execute(
                        ImageRequest.Builder(context).data(url).allowHardware(false).build()
                    )
                    result.drawable?.toBitmap()?.let(::extractArtworkAccent)
                }.getOrNull()
            }
            if (accent != null) {
                artworkAccentCache[mediaId] = accent
                if (requestedActiveMediaId == mediaId) _dynamicAccentArgb.value = accent
            }
        }
    }

}
