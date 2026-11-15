@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
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

    private val _tracks = MutableStateFlow(exoPlayer.currentTracks.toPlayerTracks())
    override val tracks: StateFlow<PlayerTracks> = _tracks.asStateFlow()

    private val _cues = MutableStateFlow(exoPlayer.currentCues)
    override val cues: StateFlow<PlayerCues> = _cues.asStateFlow()

    private val _videoOutput = MutableStateFlow<Any?>(exoPlayer)
    override val videoOutput: StateFlow<Any?> = _videoOutput.asStateFlow()

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
            _tracks.value = tracks.toPlayerTracks()
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

    override fun setMedia(uri: String, subtitles: List<ExternalSubtitle>) {
        Log.i("ExoPlayerEngine", "Setting media: $uri")
        _videoDecoderName.value = null
        _audioDecoderName.value = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setSubtitleConfigurations(subtitles.map { it.toSubtitleConfiguration() })
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    override fun setSubtitleDelay(delayMs: Long) {
        exoPlayer.subtitleDelayMilliseconds = delayMs
    }

    override fun selectTrack(track: PlayerTrack) {
        val group = track.handle as? TrackGroup ?: return
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group, track.indexInGroup))
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

internal fun Tracks.toPlayerTracks(): PlayerTracks = PlayerTracks(
    groups.map { group ->
        val id = group.mediaTrackGroup.id
        PlayerTrackGroup(
            type = group.type,
            id = id,
            tracks = (0 until group.length).map { index ->
                val format = group.getTrackFormat(index)
                PlayerTrack(
                    type = group.type,
                    groupId = id,
                    indexInGroup = index,
                    label = format.label,
                    language = format.language,
                    isSelected = group.isTrackSelected(index),
                    isSupported = group.isTrackSupported(index),
                    handle = group.mediaTrackGroup
                )
            }
        )
    }
)

internal fun ExternalSubtitle.toSubtitleConfiguration(): MediaItem.SubtitleConfiguration =
    MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
        .setMimeType(mimeType)
        .setLabel(label)
        .setLanguage(language)
        .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
        .build()
