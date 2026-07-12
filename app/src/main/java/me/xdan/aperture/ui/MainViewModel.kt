package me.xdan.aperture.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.xdan.aperture.data.remote.api.TmdbApi

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaRepository: MediaRepository,
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

    val isTutorialRequired = userPreferencesRepository.isTutorialRequired.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    val tutorialExampleMedia = mediaRepository.getAllMedia()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            userPreferencesRepository.isOnboardingCompleted
                .filter { it }
                .first()
            startLibraryPreparation()
        }
    }

    fun startLibraryPreparation() {
        if (preparationJob?.isActive == true) return
        if (
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

    fun setActiveMedia(mediaId: Long) {
        if (themeId.value != "dynamic") return
        requestedActiveMediaId = mediaId
        artworkAccentCache[mediaId]?.let {
            _dynamicAccentArgb.value = it
            return
        }
        viewModelScope.launch {
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

    private fun extractArtworkAccent(source: Bitmap): Int {
        val bitmap = Bitmap.createScaledBitmap(source, 32, 32, true)
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        var totalWeight = 0.0
        val hsv = FloatArray(3)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            Color.colorToHSV(pixel, hsv)
            val value = hsv[2]
            val saturation = hsv[1]
            if (Color.alpha(pixel) < 180 || value < 0.14f || value > 0.92f) continue
            val weight = (0.35f + saturation).toDouble()
            red += Color.red(pixel) * weight
            green += Color.green(pixel) * weight
            blue += Color.blue(pixel) * weight
            totalWeight += weight
        }
        if (bitmap !== source) bitmap.recycle()
        if (totalWeight == 0.0) return 0xFFD0BCFF.toInt()
        val raw = Color.rgb(
            (red / totalWeight).toInt().coerceIn(0, 255),
            (green / totalWeight).toInt().coerceIn(0, 255),
            (blue / totalWeight).toInt().coerceIn(0, 255)
        )
        Color.colorToHSV(raw, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.35f)
        hsv[2] = hsv[2].coerceIn(0.68f, 0.92f)
        return Color.HSVToColor(hsv)
    }
}
