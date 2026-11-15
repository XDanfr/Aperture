package me.xdan.aperture.ui.screen.player

import kotlinx.coroutines.flow.StateFlow

/**
 * A playback backend. Android implements it with Media3/ExoPlayer, desktop with libmpv. Values
 * deliberately match Media3's constants so the Android engine passes them straight through.
 */
interface PlayerEngine {
    val isPlaying: StateFlow<Boolean>
    /** One of [PlaybackStates]. */
    val playbackState: StateFlow<Int>
    val currentPosition: Long
    val duration: Long
    val videoDecoderName: StateFlow<String?>
    val audioDecoderName: StateFlow<String?>
    val tracks: StateFlow<PlayerTracks>
    val cues: StateFlow<PlayerCues>

    /** Whatever the platform's [PlayerVideoSurface] draws from; null until an engine exists. */
    val videoOutput: StateFlow<Any?>

    fun play()
    fun pause()
    fun stop()
    fun release()
    fun seekTo(positionMs: Long)
    fun setMedia(uri: String, subtitles: List<ExternalSubtitle>)
    fun setSubtitleDelay(delayMs: Long)
    fun selectTrack(track: PlayerTrack)
    /** [trackType] is one of [TrackTypes]. */
    fun setTrackTypeDisabled(trackType: Int, disabled: Boolean)
    fun clearTrackOverrides(trackType: Int)
    fun setTunnelingEnabled(enabled: Boolean)

    interface Listener {
        fun onPlaybackStateChanged(state: Int)
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onPlayerError(error: Throwable)
        fun onPositionDiscontinuity(reason: Int)
    }

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)
}

/** Media3 Player.STATE_* values. */
object PlaybackStates {
    const val IDLE = 1
    const val BUFFERING = 2
    const val READY = 3
    const val ENDED = 4
}

/** Media3 C.TRACK_TYPE_* values. */
object TrackTypes {
    const val AUDIO = 1
    const val VIDEO = 2
    const val TEXT = 3
}

/** Media3 AspectRatioFrameLayout.RESIZE_MODE_* values. */
object VideoResizeModes {
    const val FIT = 0
    const val FILL = 3
    const val ZOOM = 4
}

data class PlayerTracks(val groups: List<PlayerTrackGroup> = emptyList()) {
    companion object {
        val EMPTY = PlayerTracks()
    }
}

/** One selectable group: an audio stream with its variants, a subtitle file, and so on. */
data class PlayerTrackGroup(
    val type: Int,
    /** Stable id for list keys. */
    val id: String,
    val tracks: List<PlayerTrack>
)

data class PlayerTrack(
    val type: Int,
    val groupId: String,
    val indexInGroup: Int,
    val label: String?,
    val language: String?,
    val isSelected: Boolean,
    val isSupported: Boolean,
    /** The engine's own handle for this track (a Media3 TrackGroup, an mpv track id). */
    internal val handle: Any?
)

/** A sidecar or downloaded subtitle file to load alongside the video. */
data class ExternalSubtitle(
    val uri: String,
    val mimeType: String,
    val label: String,
    val language: String?,
    val isDefault: Boolean
)

/** The subtitle cues on screen right now, in the engine's own form. */
expect class PlayerCues
