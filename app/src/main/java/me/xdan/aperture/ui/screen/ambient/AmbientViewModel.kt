package me.xdan.aperture.ui.screen.ambient

import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.domain.model.AmbientSettings
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import me.xdan.aperture.ui.artwork.extractArtworkAccent
import javax.inject.Inject

@HiltViewModel
class AmbientViewModel @Inject constructor(
    mediaRepository: MediaRepository,
    userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val media: StateFlow<List<MediaEntity>> = mediaRepository.getAllMedia()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AmbientSettings> = combine(
        userPreferencesRepository.ambientMode,
        userPreferencesRepository.ambientWallBrandPlacement,
        userPreferencesRepository.ambientShowClock
    ) { mode, placement, showClock ->
        AmbientSettings(mode, placement, showClock)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AmbientSettings()
    )

    private val accentCache = mutableMapOf<String, Int>()
    private val accentCacheMutex = Mutex()

    suspend fun artworkAccent(path: String): Int? {
        accentCacheMutex.withLock { accentCache[path] }?.let { return it }
        val accent = withContext(Dispatchers.IO) {
            runCatching {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        // The same URL is used by the cinematic renderer, so extracting the
                        // next accent also warms Coil's disk cache before the crossfade.
                        .data(TmdbApi.IMAGE_BASE_URL + "w1280" + path)
                        .allowHardware(false)
                        .build()
                )
                result.drawable?.toBitmap()?.let(::extractArtworkAccent)
            }.getOrNull()
        } ?: return null
        accentCacheMutex.withLock { accentCache[path] = accent }
        return accent
    }
}
