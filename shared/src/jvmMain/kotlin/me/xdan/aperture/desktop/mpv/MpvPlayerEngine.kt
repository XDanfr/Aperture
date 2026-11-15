package me.xdan.aperture.desktop.mpv

import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import me.xdan.aperture.platform.Log
import me.xdan.aperture.ui.screen.player.ExternalSubtitle
import me.xdan.aperture.ui.screen.player.PlaybackStates
import me.xdan.aperture.ui.screen.player.PlayerCues
import me.xdan.aperture.ui.screen.player.PlayerEngine
import me.xdan.aperture.ui.screen.player.PlayerTrack
import me.xdan.aperture.ui.screen.player.PlayerTrackGroup
import me.xdan.aperture.ui.screen.player.PlayerTracks
import me.xdan.aperture.ui.screen.player.TrackTypes
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [PlayerEngine] on libmpv, the desktop counterpart of the Media3 engine. It reproduces the
 * contract the shared player UI relies on: Media3's playback states, a paused prepare until
 * play(), seeks queued before the file loads, sidecar subtitles, and track selection.
 *
 * mpv is created lazily and torn down by [release], as Android's PlayerEngineManager does with
 * ExoPlayer, so the singleton survives leaving and re-entering the player.
 */
class MpvPlayerEngine : PlayerEngine {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    private val listeners = CopyOnWriteArrayList<PlayerEngine.Listener>()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackStates.IDLE)
    override val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _videoDecoderName = MutableStateFlow<String?>(null)
    override val videoDecoderName: StateFlow<String?> = _videoDecoderName.asStateFlow()

    private val _audioDecoderName = MutableStateFlow<String?>(null)
    override val audioDecoderName: StateFlow<String?> = _audioDecoderName.asStateFlow()

    private val _tracks = MutableStateFlow(PlayerTracks.EMPTY)
    override val tracks: StateFlow<PlayerTracks> = _tracks.asStateFlow()

    private val _cues = MutableStateFlow(PlayerCues(""))
    override val cues: StateFlow<PlayerCues> = _cues.asStateFlow()

    private val _videoOutput = MutableStateFlow<Any?>(null)
    override val videoOutput: StateFlow<Any?> = _videoOutput.asStateFlow()

    // Session state, written on the event thread and read anywhere.
    @Volatile private var session: Session? = null
    @Volatile private var positionSeconds = 0.0
    @Volatile private var durationSeconds = 0.0
    @Volatile private var fileLoaded = false
    @Volatile private var paused = true
    @Volatile private var pausedForCache = false
    @Volatile private var eofReached = false
    @Volatile private var seeking = false
    @Volatile private var wantPlay = false
    @Volatile private var pendingSeekMs: Long? = null
    @Volatile private var pendingSubtitles: List<ExternalSubtitle> = emptyList()
    @Volatile private var hwdec: String? = null
    @Volatile private var videoCodec: String? = null

    override val currentPosition: Long
        get() = pendingSeekMs ?: (positionSeconds * 1000).toLong()

    override val duration: Long
        get() = if (durationSeconds > 0) (durationSeconds * 1000).toLong() else TIME_UNSET

    /** Video output for [me.xdan.aperture.ui.screen.player.PlayerVideoSurface]. */
    internal val output: MpvVideoOutput? get() = session?.output

    private class Session(val lib: LibMpv, val handle: Pointer, val output: MpvVideoOutput, val thread: Thread) {
        /** Set before the handle is destroyed; the event loop must be out of mpv by then. */
        @Volatile var closing = false
    }

    @Synchronized
    private fun ensureSession(): Session? {
        session?.let { return it }
        val lib = LibMpv.instance ?: run {
            dispatchError(IllegalStateException(LibMpv.loadError ?: "libmpv unavailable"))
            return null
        }
        val handle = lib.createHandle() ?: run {
            dispatchError(IllegalStateException("mpv_create failed"))
            return null
        }
        OPTIONS.forEach { (name, value) -> lib.mpv_set_option_string(handle, name, value) }
        lib.mpv_request_log_messages(handle, "warn")
        val status = lib.mpv_initialize(handle)
        if (status < 0) {
            lib.mpv_terminate_destroy(handle)
            dispatchError(IllegalStateException("mpv_initialize failed: ${lib.mpv_error_string(status)}"))
            return null
        }
        OBSERVED.forEachIndexed { index, (name, format) ->
            lib.mpv_observe_property(handle, index.toLong() + 1, name, format)
        }
        val output = MpvVideoOutput(lib, handle)
        lateinit var created: Session
        val thread = Thread({ eventLoop(created) }, "mpv-events").apply { isDaemon = true }
        created = Session(lib, handle, output, thread)
        session = created
        thread.start()
        _videoOutput.value = output
        return created
    }

    // region commands

    private fun Session.command(vararg args: String) {
        val status = lib.mpv_command(handle, arrayOf(*args))
        if (status < 0) Log.w(TAG, "${args.first()} failed: ${lib.mpv_error_string(status)}")
    }

    private fun Session.set(name: String, value: String) {
        lib.mpv_set_property_string(handle, name, value)
    }

    override fun setMedia(uri: String, subtitles: List<ExternalSubtitle>) {
        val s = ensureSession() ?: return
        Log.i(TAG, "Setting media: $uri")
        _videoDecoderName.value = null
        _audioDecoderName.value = null
        fileLoaded = false
        eofReached = false
        wantPlay = false
        pendingSeekMs = null
        positionSeconds = 0.0
        durationSeconds = 0.0
        pendingSubtitles = subtitles
        _tracks.value = PlayerTracks.EMPTY
        _cues.value = PlayerCues("")
        // Like ExoPlayer.prepare(): load paused, start on play().
        s.set("pause", "yes")
        s.command("loadfile", uri, "replace")
        updateState(PlaybackStates.BUFFERING)
    }

    override fun play() {
        wantPlay = true
        val s = session ?: return
        if (fileLoaded) s.set("pause", "no")
    }

    override fun pause() {
        wantPlay = false
        session?.set("pause", "yes")
    }

    override fun stop() {
        wantPlay = false
        pendingSeekMs = null
        val s = session ?: return
        s.command("stop")
        fileLoaded = false
        updateState(PlaybackStates.IDLE)
    }

    override fun release() {
        val s = synchronized(this) { session.also { session = null } } ?: return
        _videoOutput.value = null
        // The handle must outlive every mpv_wait_event call: stop the event loop first.
        s.closing = true
        s.lib.mpv_wakeup(s.handle)
        s.thread.join(5_000)
        if (s.thread.isAlive) Log.w(TAG, "Event loop didn't stop; destroying mpv anyway")
        // The render context goes before the handle, as render.h requires.
        s.output.release()
        s.lib.mpv_terminate_destroy(s.handle)
        fileLoaded = false
        _isPlaying.value = false
        updateState(PlaybackStates.IDLE)
    }

    override fun seekTo(positionMs: Long) {
        val s = session
        if (s == null || !fileLoaded) {
            // Applied once the file has loaded, before playback starts.
            pendingSeekMs = positionMs.coerceAtLeast(0L)
            return
        }
        eofReached = false
        s.command("seek", seconds(positionMs), "absolute+exact")
    }

    override fun setSubtitleDelay(delayMs: Long) {
        session?.set("sub-delay", seconds(delayMs))
    }

    override fun selectTrack(track: PlayerTrack) {
        val s = session ?: return
        val id = track.handle as? Long ?: return
        when (track.type) {
            TrackTypes.AUDIO -> s.set("aid", id.toString())
            TrackTypes.TEXT -> s.set("sid", id.toString())
            TrackTypes.VIDEO -> s.set("vid", id.toString())
        }
    }

    override fun setTrackTypeDisabled(trackType: Int, disabled: Boolean) {
        if (!disabled) return // re-enabling happens through the following selectTrack
        val s = session ?: return
        when (trackType) {
            TrackTypes.AUDIO -> s.set("aid", "no")
            TrackTypes.TEXT -> s.set("sid", "no")
            TrackTypes.VIDEO -> s.set("vid", "no")
        }
    }

    override fun clearTrackOverrides(trackType: Int) {
        // Audio returns to mpv's own choice. Text keeps whatever "Off" or a selection set,
        // matching how the shared UI pairs this call with setTrackTypeDisabled.
        if (trackType == TrackTypes.AUDIO) session?.set("aid", "auto")
    }

    // Tunnelled playback is an Android TV concept.
    override fun setTunnelingEnabled(enabled: Boolean) = Unit

    override fun addListener(listener: PlayerEngine.Listener) {
        listeners += listener
    }

    override fun removeListener(listener: PlayerEngine.Listener) {
        listeners -= listener
    }

    // endregion

    // region events

    private fun eventLoop(s: Session) {
        while (!s.closing) {
            val event = s.lib.mpv_wait_event(s.handle, -1.0)
            if (s.closing) return
            when (event.getInt(LibMpv.EVENT_ID_OFFSET)) {
                LibMpv.EVENT_SHUTDOWN -> return
                LibMpv.EVENT_START_FILE -> updateState(PlaybackStates.BUFFERING)
                LibMpv.EVENT_FILE_LOADED -> onFileLoaded(s)
                LibMpv.EVENT_SEEK -> {
                    seeking = true
                    updateState(PlaybackStates.BUFFERING)
                }
                LibMpv.EVENT_PLAYBACK_RESTART -> {
                    seeking = false
                    refreshState()
                }
                LibMpv.EVENT_END_FILE -> onEndFile(s, event.getPointer(LibMpv.EVENT_DATA_OFFSET))
                LibMpv.EVENT_PROPERTY_CHANGE -> onPropertyChange(s, event.getPointer(LibMpv.EVENT_DATA_OFFSET))
                LibMpv.EVENT_LOG_MESSAGE -> {
                    val data = event.getPointer(LibMpv.EVENT_DATA_OFFSET)
                    val prefix = data.getPointer(LibMpv.LOG_PREFIX_OFFSET)?.getString(0)
                    val level = data.getPointer(LibMpv.LOG_LEVEL_OFFSET)?.getString(0)
                    val text = data.getPointer(LibMpv.LOG_TEXT_OFFSET)?.getString(0)?.trimEnd()
                    Log.w(TAG, "[$level] $prefix: $text")
                }
            }
        }
    }

    private fun onFileLoaded(s: Session) {
        fileLoaded = true
        pendingSubtitles.forEachIndexed { index, subtitle ->
            // ExoPlayer shows a default-flagged sidecar track; "select" is mpv's equivalent.
            val flag = if (subtitle.isDefault && index == 0) "select" else "auto"
            s.command("sub-add", subtitle.uri, flag, subtitle.label, subtitle.language ?: "")
        }
        pendingSeekMs?.let { ms ->
            pendingSeekMs = null
            if (ms > 0) s.command("seek", seconds(ms), "absolute+exact")
        }
        s.set("pause", if (wantPlay) "no" else "yes")
    }

    private fun onEndFile(s: Session, data: Pointer) {
        val reason = data.getInt(0)
        val error = data.getInt(4)
        if (reason == LibMpv.END_FILE_REASON_ERROR) {
            fileLoaded = false
            updateState(PlaybackStates.IDLE)
            dispatchError(RuntimeException("Playback error: ${s.lib.mpv_error_string(error)}"))
        }
    }

    private fun onPropertyChange(s: Session, data: Pointer) {
        val name = data.getPointer(LibMpv.PROPERTY_NAME_OFFSET).getString(0)
        val format = data.getInt(LibMpv.PROPERTY_FORMAT_OFFSET)
        val value = data.getPointer(LibMpv.PROPERTY_DATA_OFFSET)
        fun flag() = format == LibMpv.FORMAT_FLAG && value.getInt(0) != 0
        fun double() = if (format == LibMpv.FORMAT_DOUBLE) value.getDouble(0) else 0.0
        fun string() = if (format == LibMpv.FORMAT_STRING) value.getPointer(0)?.getString(0, "UTF-8") else null

        when (name) {
            "time-pos" -> positionSeconds = double()
            "duration" -> durationSeconds = double()
            "pause" -> { paused = flag(); refreshState() }
            "paused-for-cache" -> { pausedForCache = flag(); refreshState() }
            "eof-reached" -> {
                val reached = flag()
                if (reached != eofReached) {
                    eofReached = reached
                    refreshState()
                }
            }
            "sub-text" -> _cues.value = PlayerCues(string().orEmpty())
            "hwdec-current" -> { hwdec = string(); publishDecoders(s) }
            "video-codec" -> { videoCodec = string(); publishDecoders(s) }
            "audio-codec-name" -> _audioDecoderName.value = string()?.let { "mpv · $it" }
            "track-list" -> _tracks.value = readTracks(s)
        }
    }

    private fun publishDecoders(s: Session) {
        val codec = videoCodec ?: return
        val decoder = hwdec?.takeIf { it.isNotBlank() && it != "no" } ?: "software"
        _videoDecoderName.value = "mpv · $codec ($decoder)"
    }

    /** One group per mpv track; mpv numbers tracks per type from 1. */
    private fun readTracks(s: Session): PlayerTracks {
        val count = s.getString("track-list/count")?.toIntOrNull() ?: return PlayerTracks.EMPTY
        val groups = (0 until count).mapNotNull { index ->
            val prefix = "track-list/$index"
            val type = when (s.getString("$prefix/type")) {
                "audio" -> TrackTypes.AUDIO
                "sub" -> TrackTypes.TEXT
                "video" -> TrackTypes.VIDEO
                else -> return@mapNotNull null
            }
            val id = s.getString("$prefix/id")?.toLongOrNull() ?: return@mapNotNull null
            val groupId = "$type:$id"
            val language = s.getString("$prefix/lang")
            PlayerTrackGroup(
                type = type,
                id = groupId,
                tracks = listOf(
                    PlayerTrack(
                        type = type,
                        groupId = groupId,
                        indexInGroup = 0,
                        label = s.getString("$prefix/title"),
                        language = language,
                        isSelected = s.getString("$prefix/selected") == "yes",
                        isSupported = true,
                        handle = id
                    )
                )
            )
        }
        return PlayerTracks(groups)
    }

    private fun Session.getString(name: String): String? {
        val pointer = lib.mpv_get_property_string(handle, name) ?: return null
        return try {
            pointer.getString(0, "UTF-8")
        } finally {
            lib.mpv_free(pointer)
        }
    }

    /** Maps mpv's flags onto Media3's IDLE/BUFFERING/READY/ENDED and isPlaying. */
    private fun refreshState() {
        val state = when {
            !fileLoaded -> _playbackState.value
            eofReached -> PlaybackStates.ENDED
            pausedForCache || seeking -> PlaybackStates.BUFFERING
            else -> PlaybackStates.READY
        }
        updateState(state)
        val playing = fileLoaded && state == PlaybackStates.READY && !paused
        if (playing != _isPlaying.value) {
            _isPlaying.value = playing
            mainScope.launch { listeners.forEach { it.onIsPlayingChanged(playing) } }
        }
    }

    private fun updateState(state: Int) {
        if (state == _playbackState.value) return
        _playbackState.value = state
        mainScope.launch { listeners.forEach { it.onPlaybackStateChanged(state) } }
    }

    private fun dispatchError(error: Throwable) {
        Log.e(TAG, error.message ?: "mpv error", error)
        mainScope.launch { listeners.forEach { it.onPlayerError(error) } }
    }

    // endregion

    private companion object {
        const val TAG = "MpvPlayerEngine"
        /** Media3's C.TIME_UNSET. */
        const val TIME_UNSET = Long.MIN_VALUE + 1

        fun seconds(ms: Long) = String.format(Locale.ROOT, "%.3f", ms / 1000.0)

        val OPTIONS = listOf(
            "vo" to "libmpv",
            "hwdec" to "auto-copy",
            "keep-open" to "yes",
            "idle" to "yes",
            "config" to "no",
            "terminal" to "no",
            "osc" to "no",
            "input-default-bindings" to "no",
            "input-vo-keyboard" to "no",
            // Aperture draws subtitles itself from sub-text, in its own style.
            "sub-visibility" to "no",
            // Aperture finds sidecar subtitles itself and passes them in; mpv's own search would
            // add each one a second time.
            "sub-auto" to "no",
            "stop-screensaver" to "yes",
            "hr-seek" to "yes",
        )

        val OBSERVED = listOf(
            "time-pos" to LibMpv.FORMAT_DOUBLE,
            "duration" to LibMpv.FORMAT_DOUBLE,
            "pause" to LibMpv.FORMAT_FLAG,
            "paused-for-cache" to LibMpv.FORMAT_FLAG,
            "eof-reached" to LibMpv.FORMAT_FLAG,
            "sub-text" to LibMpv.FORMAT_STRING,
            "hwdec-current" to LibMpv.FORMAT_STRING,
            "video-codec" to LibMpv.FORMAT_STRING,
            "audio-codec-name" to LibMpv.FORMAT_STRING,
            "track-list" to LibMpv.FORMAT_NONE,
        )
    }
}
