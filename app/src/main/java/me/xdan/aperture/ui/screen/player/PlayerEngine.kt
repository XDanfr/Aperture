package me.xdan.aperture.ui.screen.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Tracks
import kotlinx.coroutines.flow.StateFlow

interface PlayerEngine {
    val isPlaying: StateFlow<Boolean>
    val playbackState: StateFlow<Int>
    val currentPosition: Long
    val duration: Long
    val videoDecoderName: StateFlow<String?>
    val audioDecoderName: StateFlow<String?>
    val tracks: StateFlow<Tracks>
    val cues: StateFlow<androidx.media3.common.text.CueGroup>

    fun play()
    fun pause()
    fun stop()
    fun release()
    fun seekTo(positionMs: Long)
    fun setMedia(uri: Uri, subtitles: List<MediaItem.SubtitleConfiguration>)
    fun setSubtitleDelay(delayMs: Long)
    fun setTrackSelectionOverride(group: androidx.media3.common.TrackGroup, trackIndex: Int)
    fun setTrackTypeDisabled(trackType: Int, disabled: Boolean)
    fun clearTrackOverrides(trackType: Int)
    fun setTunnelingEnabled(enabled: Boolean)

    val nativePlayer: StateFlow<androidx.media3.common.Player?>

    interface Listener {
        fun onPlaybackStateChanged(state: Int)
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onPlayerError(error: Throwable)
        fun onPositionDiscontinuity(reason: Int)
    }

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)
}
