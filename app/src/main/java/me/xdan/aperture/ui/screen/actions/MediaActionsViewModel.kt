package me.xdan.aperture.ui.screen.actions

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

data class MediaActionState(
    val media: MediaEntity? = null,
    val progress: PlaybackProgressEntity? = null
)

@HiltViewModel
class MediaActionsViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {
    private val _state = MutableStateFlow(MediaActionState())
    val state: StateFlow<MediaActionState> = _state

    fun load(mediaId: Long) {
        _state.value = MediaActionState()
        viewModelScope.launch { refresh(mediaId) }
    }

    fun clearProgress(mediaId: Long) = viewModelScope.launch {
        repository.clearProgress(mediaId)
        refresh(mediaId)
    }

    fun toggleFavorite(mediaId: Long) = viewModelScope.launch {
        val media = repository.getMediaById(mediaId) ?: return@launch
        repository.setFavorite(mediaId, !media.isFavorite)
        refresh(mediaId)
    }

    fun toggleWatched(mediaId: Long) = viewModelScope.launch {
        val media = repository.getMediaById(mediaId) ?: return@launch
        val current = repository.getProgress(mediaId)
        if (current?.isCompleted == true) {
            repository.clearProgress(mediaId)
        } else {
            val duration = (current?.duration ?: media.duration ?: 1L).coerceAtLeast(1L)
            repository.saveProgress(
                PlaybackProgressEntity(
                    mediaId = mediaId,
                    position = duration,
                    duration = duration,
                    lastUpdated = System.currentTimeMillis(),
                    isCompleted = true,
                    completedAt = System.currentTimeMillis()
                )
            )
        }
        refresh(mediaId)
    }

    fun hide(mediaId: Long) = viewModelScope.launch { repository.setHidden(mediaId, true) }

    fun refreshAssets(mediaId: Long) = viewModelScope.launch {
        repository.getMediaById(mediaId)?.let { repository.syncMetadata(it.copy(metadataAttemptedAt = null)) }
        refresh(mediaId)
    }

    private suspend fun refresh(mediaId: Long) {
        _state.value = MediaActionState(repository.getMediaById(mediaId), repository.getProgress(mediaId))
    }
}
