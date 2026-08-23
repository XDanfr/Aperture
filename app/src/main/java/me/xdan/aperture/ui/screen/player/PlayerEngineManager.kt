@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Provider

class PlayerEngineManager @Inject constructor(
    private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val factory: PlayerEngineFactory
) : PlayerEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentEngine: PlayerEngine? = null
    private val listeners = mutableListOf<PlayerEngine.Listener>()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(1) // STATE_IDLE
    override val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    override val currentPosition: Long
        get() = currentEngine?.currentPosition ?: 0L

    override val duration: Long
        get() = currentEngine?.duration ?: 0L

    private val _videoDecoderName = MutableStateFlow<String?>(null)
    override val videoDecoderName: StateFlow<String?> = _videoDecoderName.asStateFlow()

    private val _audioDecoderName = MutableStateFlow<String?>(null)
    override val audioDecoderName: StateFlow<String?> = _audioDecoderName.asStateFlow()

    private val _tracks = MutableStateFlow(Tracks.EMPTY)
    override val tracks: StateFlow<Tracks> = _tracks.asStateFlow()

    private val _cues = MutableStateFlow(CueGroup.EMPTY_TIME_ZERO)
    override val cues: StateFlow<CueGroup> = _cues.asStateFlow()

    private val _nativePlayer = MutableStateFlow<androidx.media3.common.Player?>(null)
    override val nativePlayer: StateFlow<androidx.media3.common.Player?> = _nativePlayer.asStateFlow()

    private var lastMediaUri: Uri? = null
    private var lastSubtitles: List<MediaItem.SubtitleConfiguration> = emptyList()
    private var autoFallbackEnabled = true

    private val engineListener = object : PlayerEngine.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.value = state
            listeners.forEach { it.onPlaybackStateChanged(state) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            listeners.forEach { it.onIsPlayingChanged(isPlaying) }
        }

        override fun onPlayerError(error: Throwable) {
            Log.e("PlayerEngineManager", "Engine error: ${error.message}", error)
            if (autoFallbackEnabled && shouldFallback(error)) {
                performFallback()
            } else {
                listeners.forEach { it.onPlayerError(error) }
            }
        }

        override fun onPositionDiscontinuity(reason: Int) {
            listeners.forEach { it.onPositionDiscontinuity(reason) }
        }
    }

    private fun shouldFallback(error: Throwable): Boolean {
        // Fallback on decoding errors or explicit "green screen" indicators if possible
        val message = error.message?.lowercase() ?: ""
        return message.contains("decoding") || message.contains("codec") || message.contains("renderer")
    }

    private fun performFallback() {
        val position = currentPosition
        val uri = lastMediaUri ?: return
        val subs = lastSubtitles
        autoFallbackEnabled = false // Prevent infinite loops
        
        Log.i("PlayerEngineManager", "Performing fallback to Compatibility Engine at $position")
        
        scope.launch {
            commandMutex.withLock {
                Log.d("PlayerEngineManager", "Fallback lock acquired")
                val oldEngine = currentEngine
                oldEngine?.removeListener(engineListener)
                oldEngine?.release()
                currentEngine = null
                _nativePlayer.value = null
                
                val engine = factory.createExoPlayerEngine(forceSoftware = true, forceNoTunneling = true)
                engine.addListener(engineListener)
                
                scope.launch { engine.videoDecoderName.collect { name -> _videoDecoderName.value = name } }
                scope.launch { engine.audioDecoderName.collect { name -> _audioDecoderName.value = name } }
                scope.launch { engine.tracks.collect { t -> _tracks.value = t } }
                scope.launch { engine.cues.collect { c -> _cues.value = c } }
                scope.launch { engine.nativePlayer.collect { p -> _nativePlayer.value = p } }
                
                currentEngine = engine
                Log.d("PlayerEngineManager", "Compatibility engine created, restarting media")
                
                engine.setMedia(uri, subs)
                engine.seekTo(position)
                engine.play()
            }
        }
    }

    private val commandMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun ensureEngineLocked(): PlayerEngine {
        var engine = currentEngine
        if (engine == null) {
            val engineType = preferences.playbackEngine.first()
            Log.i("PlayerEngineManager", "Creating engine of type: $engineType")
            engine = try {
                when (engineType) {
                    "compatibility" -> factory.createExoPlayerEngine(
                        forceSoftware = true, 
                        forceNoTunneling = true,
                        force8Bit = true // Force 8-bit for compatibility mode for testing
                    )
                    else -> factory.createExoPlayerEngine()
                }
            } catch (e: Exception) {
                Log.e("PlayerEngineManager", "Factory failed to create engine", e)
                throw e
            }
            
            engine.also {
                it.addListener(engineListener)
                // Use a single supervisor scope for all collectors to avoid leaks
                scope.launch { it.videoDecoderName.collect { name -> _videoDecoderName.value = name } }
                scope.launch { it.audioDecoderName.collect { name -> _audioDecoderName.value = name } }
                scope.launch { it.tracks.collect { t -> _tracks.value = t } }
                scope.launch { it.cues.collect { c -> _cues.value = c } }
                scope.launch { it.nativePlayer.collect { p -> _nativePlayer.value = p } }
                currentEngine = it
            }
        }
        return engine
    }

    override fun play() {
        scope.launch {
            commandMutex.withLock {
                ensureEngineLocked().play()
            }
        }
    }

    override fun pause() {
        scope.launch {
            commandMutex.withLock {
                currentEngine?.pause()
            }
        }
    }

    override fun stop() {
        scope.launch {
            commandMutex.withLock {
                currentEngine?.stop()
            }
        }
    }

    override fun release() {
        scope.launch {
            commandMutex.withLock {
                val engine = currentEngine
                engine?.removeListener(engineListener)
                engine?.release()
                currentEngine = null
                _nativePlayer.value = null
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        scope.launch {
            commandMutex.withLock {
                currentEngine?.seekTo(positionMs)
            }
        }
    }

    override fun setMedia(uri: Uri, subtitles: List<MediaItem.SubtitleConfiguration>) {
        lastMediaUri = uri
        lastSubtitles = subtitles
        autoFallbackEnabled = true
        scope.launch {
            commandMutex.withLock {
                ensureEngineLocked().setMedia(uri, subtitles)
            }
        }
    }

    override fun setSubtitleDelay(delayMs: Long) {
        scope.launch {
            commandMutex.withLock {
                currentEngine?.setSubtitleDelay(delayMs)
            }
        }
    }

    override fun setTrackSelectionOverride(group: androidx.media3.common.TrackGroup, trackIndex: Int) {
        scope.launch {
            commandMutex.withLock {
                currentEngine?.setTrackSelectionOverride(group, trackIndex)
            }
        }
    }

    override fun setTrackTypeDisabled(trackType: Int, disabled: Boolean) {
        scope.launch {
            commandMutex.withLock {
                currentEngine?.setTrackTypeDisabled(trackType, disabled)
            }
        }
    }

    override fun clearTrackOverrides(trackType: Int) {
        scope.launch {
            commandMutex.withLock {
                currentEngine?.clearTrackOverrides(trackType)
            }
        }
    }

    override fun setTunnelingEnabled(enabled: Boolean) {
        scope.launch {
            commandMutex.withLock {
                ensureEngineLocked().setTunnelingEnabled(enabled)
            }
        }
    }

    override fun addListener(listener: PlayerEngine.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: PlayerEngine.Listener) {
        listeners.remove(listener)
    }
}
