package me.xdan.aperture.ui.screen.ambient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.domain.model.AmbientSettings
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import javax.inject.Inject

@HiltViewModel
class AmbientViewModel @Inject constructor(
    mediaRepository: MediaRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val artworkPalettes: me.xdan.aperture.data.artwork.ArtworkPaletteStore
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

    suspend fun artworkAccent(path: String): Int? = artworkPalettes.seed(path)
}
