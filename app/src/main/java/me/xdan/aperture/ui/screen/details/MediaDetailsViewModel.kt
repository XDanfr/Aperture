package me.xdan.aperture.ui.screen.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.domain.repository.MediaRepository
import javax.inject.Inject

@HiltViewModel
class MediaDetailsViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _media = MutableStateFlow<MediaEntity?>(null)
    val media: StateFlow<MediaEntity?> = _media
    private val _progress = MutableStateFlow<PlaybackProgressEntity?>(null)
    val progress: StateFlow<PlaybackProgressEntity?> = _progress

    fun loadMedia(mediaId: Long) {
        viewModelScope.launch {
            _media.value = repository.getMediaById(mediaId)
            _progress.value = repository.getProgress(mediaId)
        }
    }

    fun toggleFavorite(mediaId: Long) {
        viewModelScope.launch {
            val current = repository.getMediaById(mediaId) ?: return@launch
            repository.setFavorite(mediaId, !current.isFavorite)
            _media.value = current.copy(isFavorite = !current.isFavorite)
        }
    }
}
