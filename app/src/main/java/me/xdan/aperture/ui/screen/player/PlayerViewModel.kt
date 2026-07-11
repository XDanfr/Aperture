package me.xdan.aperture.ui.screen.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.domain.repository.MediaRepository
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val player: ExoPlayer,
    private val repository: MediaRepository
) : ViewModel() {

    private val _media = MutableStateFlow<MediaEntity?>(null)
    val media: StateFlow<MediaEntity?> = _media

    private val _isOsdVisible = MutableStateFlow(true)
    val isOsdVisible: StateFlow<Boolean> = _isOsdVisible

    private var osdTimerJob: Job? = null
    private var progressTrackerJob: Job? = null
    private var activeMediaId: Long? = null

    fun loadMedia(mediaId: Long, startFromBeginning: Boolean = false) {
        viewModelScope.launch {
            val mediaEntity = repository.getMediaById(mediaId)
            _media.value = mediaEntity
            
            mediaEntity?.let { m ->
                activeMediaId = m.id
                val progress = repository.getProgress(m.id)
                
                // Construct file URI safely
                val file = File(m.filePath)
                val uri = Uri.fromFile(file)
                
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                val hasActiveProgress = progress?.let {
                    it.duration > 0 &&
                        it.position >= it.duration * 0.05 &&
                        it.position < it.duration * 0.95
                } == true
                val isBelowResumeThreshold = progress?.let {
                    it.duration > 0 && it.position < it.duration * 0.05
                } == true
                val shouldRestart = startFromBeginning ||
                    isBelowResumeThreshold ||
                    (progress?.isCompleted == true && !hasActiveProgress) ||
                    progress?.let {
                        it.duration > 0 && it.position >= it.duration * 0.95
                    } == true
                if (shouldRestart) {
                    player.seekTo(0)
                    progress?.let {
                        repository.saveProgress(
                            it.copy(position = 0L, lastUpdated = System.currentTimeMillis())
                        )
                    }
                } else {
                    progress?.let { player.seekTo(it.position) }
                }
                player.playWhenReady = true
                startProgressTracker(m.id)
                resetOsdTimer()
            }
        }
    }

    private fun startProgressTracker(mediaId: Long) {
        progressTrackerJob?.cancel()
        progressTrackerJob = viewModelScope.launch {
            while (true) {
                if (player.isPlaying) {
                    saveProgressSnapshot(
                        mediaId = mediaId,
                        position = player.currentPosition,
                        duration = player.duration,
                        markCompleted = false
                    )
                }
                delay(5000) // Update every 5 seconds
            }
        }
    }

    fun saveProgressNow(markCompleted: Boolean = false) {
        if (markCompleted) progressTrackerJob?.cancel()
        val mediaId = activeMediaId ?: return
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration
        viewModelScope.launch {
            saveProgressSnapshot(mediaId, position, duration, markCompleted)
        }
    }

    private suspend fun saveProgressSnapshot(
        mediaId: Long,
        position: Long,
        duration: Long,
        markCompleted: Boolean
    ) {
        val existing = repository.getProgress(mediaId)
        val safeDuration = duration.takeIf { it > 0 } ?: existing?.duration ?: 0L
        val completedNow = markCompleted ||
            (safeDuration > 0 && position >= safeDuration * 0.95)
        repository.saveProgress(
            PlaybackProgressEntity(
                mediaId = mediaId,
                position = position,
                duration = safeDuration,
                lastUpdated = System.currentTimeMillis(),
                isCompleted = existing?.isCompleted == true || completedNow,
                completedAt = when {
                    completedNow -> System.currentTimeMillis()
                    else -> existing?.completedAt
                }
            )
        )
    }

    fun toggleOsd() {
        _isOsdVisible.value = !_isOsdVisible.value
        if (_isOsdVisible.value) {
            resetOsdTimer()
        }
    }

    fun hideOsd() {
        osdTimerJob?.cancel()
        _isOsdVisible.value = false
    }

    fun showOsdBriefly() {
        _isOsdVisible.value = true
        resetOsdTimer()
    }

    private fun resetOsdTimer() {
        osdTimerJob?.cancel()
        osdTimerJob = viewModelScope.launch {
            delay(3000)
            _isOsdVisible.value = false
        }
    }

    fun seekForward() {
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + 10000).coerceAtMost(duration))
        showOsdBriefly()
    }

    fun seekBackward() {
        player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
        showOsdBriefly()
    }

    override fun onCleared() {
        super.onCleared()
        player.stop()
        progressTrackerJob?.cancel()
        osdTimerJob?.cancel()
    }
}
