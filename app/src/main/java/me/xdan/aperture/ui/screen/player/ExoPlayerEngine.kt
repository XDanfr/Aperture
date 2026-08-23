@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleDelayMilliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExoPlayerEngine(
    private val exoPlayer: ExoPlayer
) : PlayerEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val listeners = mutableListOf<PlayerEngine.Listener>()

    private val _isPlaying = MutableStateFlow(exoPlayer.isPlaying)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(exoPlayer.playbackState)
    override val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    override val currentPosition: Long
        get() = exoPlayer.currentPosition

    override val duration: Long
        get() = exoPlayer.duration

    private val _videoDecoderName = MutableStateFlow<String?>(null)
    override val videoDecoderName: StateFlow<String?> = _videoDecoderName.asStateFlow()

    private val _audioDecoderName = MutableStateFlow<String?>(null)
    override val audioDecoderName: StateFlow<String?> = _audioDecoderName.asStateFlow()

    private val _tracks = MutableStateFlow(exoPlayer.currentTracks)
    override val tracks: StateFlow<Tracks> = _tracks.asStateFlow()

    private val _cues = MutableStateFlow(exoPlayer.currentCues)
    override val cues: StateFlow<androidx.media3.common.text.CueGroup> = _cues.asStateFlow()

    private val _nativePlayer = MutableStateFlow<androidx.media3.common.Player?>(exoPlayer)
    override val nativePlayer: StateFlow<androidx.media3.common.Player?> = _nativePlayer.asStateFlow()

    private val exoListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            listeners.forEach { it.onIsPlayingChanged(isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _playbackState.value = playbackState
            listeners.forEach { it.onPlaybackStateChanged(playbackState) }
        }

        override fun onPlayerError(error: PlaybackException) {
            listeners.forEach { it.onPlayerError(error) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            listeners.forEach { it.onPositionDiscontinuity(reason) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            _tracks.value = tracks
        }

        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            _cues.value = cueGroup
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            _videoDecoderName.value = decoderName
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            _audioDecoderName.value = decoderName
        }
    }

    init {
        exoPlayer.addListener(exoListener)
        exoPlayer.addAnalyticsListener(analyticsListener)
    }

    override fun play() {
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun stop() {
        exoPlayer.stop()
    }

    override fun release() {
        exoPlayer.removeListener(exoListener)
        exoPlayer.removeAnalyticsListener(analyticsListener)
        exoPlayer.release()
        scope.cancel()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    override fun setMedia(uri: Uri, subtitles: List<MediaItem.SubtitleConfiguration>) {
        Log.i("ExoPlayerEngine", "Setting media: $uri")
        _videoDecoderName.value = null
        _audioDecoderName.value = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(subtitles)
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    override fun setSubtitleDelay(delayMs: Long) {
        exoPlayer.subtitleDelayMilliseconds = delayMs
    }

    override fun setTrackSelectionOverride(group: TrackGroup, trackIndex: Int) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group, trackIndex))
            .build()
    }

    override fun setTrackTypeDisabled(trackType: Int, disabled: Boolean) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(trackType, disabled)
            .build()
    }

    override fun clearTrackOverrides(trackType: Int) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(trackType)
            .build()
    }

    override fun setTunnelingEnabled(enabled: Boolean) {
        (exoPlayer.trackSelector as? DefaultTrackSelector)?.let { selector ->
            selector.setParameters(
                selector.buildUponParameters()
                    .setTunnelingEnabled(enabled)
            )
        }
    }

    override fun addListener(listener: PlayerEngine.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: PlayerEngine.Listener) {
        listeners.remove(listener)
    }
}
