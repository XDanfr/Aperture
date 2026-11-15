
package me.xdan.aperture.ui.screen.player

import me.xdan.aperture.platform.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.xdan.aperture.platform.AppBuild
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.data.remote.api.OpenSubtitleResult
import me.xdan.aperture.data.remote.api.OpenSubtitlesApi
import me.xdan.aperture.data.remote.api.OpenSubtitlesDownloadRequest
import me.xdan.aperture.data.subtitles.OpenSubtitlesSessionManager
import me.xdan.aperture.data.subtitles.OpenSubtitlesSessionState
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class PlayerViewModel constructor(
    val player: PlayerEngine,
    private val repository: MediaRepository,
    private val preferences: UserPreferencesRepository,
    private val openSubtitlesApi: OpenSubtitlesApi,
    private val openSubtitlesSessionManager: OpenSubtitlesSessionManager,
    private val okHttpClient: OkHttpClient,
    private val companionHub: me.xdan.aperture.companion.CompanionHub,
    private val platform: PlaybackPlatform,
    stores: me.xdan.aperture.platform.KeyValueStores
) : ViewModel() {

    private val _media = MutableStateFlow<MediaEntity?>(null)
    val media: StateFlow<MediaEntity?> = _media
    private val _nextEpisode = MutableStateFlow<MediaEntity?>(null)
    val nextEpisode: StateFlow<MediaEntity?> = _nextEpisode
    private val _isOsdVisible = MutableStateFlow(true)
    val isOsdVisible: StateFlow<Boolean> = _isOsdVisible
    private val _onlineSubtitles = MutableStateFlow<OnlineSubtitleState>(OnlineSubtitleState.Idle)
    val onlineSubtitles: StateFlow<OnlineSubtitleState> = _onlineSubtitles
    val openSubtitlesSession: StateFlow<OpenSubtitlesSessionState> =
        openSubtitlesSessionManager.state
    private val _compatibilityWarning = MutableStateFlow<PlaybackCompatibilityWarning?>(null)
    val compatibilityWarning: StateFlow<PlaybackCompatibilityWarning?> = _compatibilityWarning
    private val _playbackFailure = MutableStateFlow<PlaybackFailure?>(null)
    val playbackFailure: StateFlow<PlaybackFailure?> = _playbackFailure
    private val _isCurrentMediaHdr = MutableStateFlow(false)
    val isCurrentMediaHdr: StateFlow<Boolean> = _isCurrentMediaHdr
    private val _subtitleDelayMs = MutableStateFlow(0L)
    val subtitleDelayMs: StateFlow<Long> = _subtitleDelayMs
    private val _isDisplayHdrCapable = MutableStateFlow(false)
    val isDisplayHdrCapable: StateFlow<Boolean> = _isDisplayHdrCapable
    
    private val _videoDecoderName = MutableStateFlow<String?>(null)
    val videoDecoderName: StateFlow<String?> = _videoDecoderName
    private val _audioDecoderName = MutableStateFlow<String?>(null)
    val audioDecoderName: StateFlow<String?> = _audioDecoderName

    val playbackEngine: StateFlow<String> = preferences.playbackEngine.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "auto"
    )

    val classicPlayerControls: StateFlow<Boolean> = preferences.classicPlayerControls.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    val shouldShowCompatibilityWarning: StateFlow<Boolean> = preferences.shouldShowCompatibilityWarning.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true
    )

    val subtitleStyle = combine(
        preferences.subtitleTextScale,
        preferences.subtitleColour,
        preferences.subtitleBackgroundOpacity
    ) { scale, colour, backgroundOpacity ->
        PlayerSubtitleStyle(scale, colour, backgroundOpacity)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlayerSubtitleStyle()
    )

    private var osdTimerJob: Job? = null
    private var progressTrackerJob: Job? = null
    private var activeMediaId: Long? = null
    private val downloadedSubtitleFiles = mutableListOf<File>()
    private var pendingPlayback: PendingPlayback? = null
    private val syncPreferences = stores.open(PLAYBACK_SYNC_PREFERENCES)

    private val playerListener = object : PlayerEngine.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                if (_isOsdVisible.value) resetOsdTimer()
            } else {
                osdTimerJob?.cancel()
                osdTimerJob = null
            }
        }

        override fun onPlaybackStateChanged(state: Int) {}
        override fun onPositionDiscontinuity(reason: Int) {}

        override fun onPlayerError(error: Throwable) {
            progressTrackerJob?.cancel()
            osdTimerJob?.cancel()
            _isOsdVisible.value = false

            val compatibility = pendingPlayback?.compatibility
            _playbackFailure.value = when {
                compatibility?.hasDolbyVision == true -> PlaybackFailure(
                    title = "This video could not be decoded",
                    message = "The device failed to play this Dolby Vision video. Try a non-Dolby Vision version, such as standard HEVC or H.264."
                )
                error.message?.contains("DECODING") == true -> PlaybackFailure(
                    title = "This video could not be decoded",
                    message = "The device failed to decode this video. It may use a format or profile that this device does not support."
                )
                else -> PlaybackFailure(
                    title = "Playback failed",
                    message = "Aperture could not play this video on this device."
                )
            }
        }
    }

    init {
        player.addListener(playerListener)
        viewModelScope.launch {
            player.videoDecoderName.collect { _videoDecoderName.value = it }
        }
        viewModelScope.launch {
            player.audioDecoderName.collect { _audioDecoderName.value = it }
        }
        checkDisplayCapabilities()
        viewModelScope.launch { _media.collect(companionHub::onPlaybackMedia) }
        viewModelScope.launch { _nextEpisode.collect { companionHub.onNextEpisodeAvailable(it != null) } }
        viewModelScope.launch {
            companionHub.playerCommands.collect { command ->
                when (command) {
                    me.xdan.aperture.companion.CompanionPlayerCommand.Toggle -> {
                        if (player.isPlaying.value) player.pause() else player.play()
                        showOsdBriefly()
                    }
                    is me.xdan.aperture.companion.CompanionPlayerCommand.SeekTo -> {
                        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        player.seekTo(command.positionMs.coerceIn(0, duration))
                        showOsdBriefly()
                    }
                    is me.xdan.aperture.companion.CompanionPlayerCommand.SeekBy -> {
                        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        player.seekTo((player.currentPosition + command.deltaMs).coerceIn(0, duration))
                        showOsdBriefly()
                    }
                    me.xdan.aperture.companion.CompanionPlayerCommand.NextEpisode -> playNextEpisode()
                    is me.xdan.aperture.companion.CompanionPlayerCommand.Load -> {
                        saveProgressNow()
                        loadMedia(command.mediaId, command.fromStart)
                    }
                }
            }
        }
    }

    private fun checkDisplayCapabilities() {
        _isDisplayHdrCapable.value = platform.isDisplayHdrCapable
    }

    fun loadMedia(mediaId: Long, startFromBeginning: Boolean = false) {
        viewModelScope.launch {
            Log.d("PlayerViewModel", "Loading media: $mediaId")
            val mediaEntity = repository.getMediaById(mediaId)
            if (mediaEntity == null) {
                Log.e("PlayerViewModel", "Media not found in repository: $mediaId")
                return@launch
            }
            Log.d("PlayerViewModel", "File path: ${mediaEntity.filePath}")
            
            progressTrackerJob?.cancel()
            _nextEpisode.value = null
            _media.value = mediaEntity
            _compatibilityWarning.value = null
            _playbackFailure.value = null
            _isCurrentMediaHdr.value = false
            _videoDecoderName.value = null
            _audioDecoderName.value = null
            restoreSyncSettings(mediaId)

            mediaEntity.let { media ->
                Log.d("PlayerViewModel", "Checking file existence: ${media.filePath}")
                val file = java.io.File(media.filePath)
                Log.d("PlayerViewModel", "Exists: ${file.exists()}, Readable: ${file.canRead()}")
                
                val tunneling = preferences.tunnelingEnabled.first()
                player.setTunnelingEnabled(tunneling)

                val compatibility = withContext(Dispatchers.IO) {
                    platform.inspectCompatibility(media.filePath)
                }
                
                if (compatibility != null) {
                    _isCurrentMediaHdr.value = compatibility.hasHdr10 || compatibility.is10Bit || compatibility.hasDolbyVision
                }

                val pending = PendingPlayback(media, startFromBeginning, compatibility)
                pendingPlayback = pending

                if (compatibility != null) {
                    val shouldShowWarning = preferences.shouldShowCompatibilityWarning.first()
                    if (shouldShowWarning) {
                        player.stop()
                        _compatibilityWarning.value = compatibility
                        return@launch
                    } else {
                        showCompatibilityToast()
                    }
                }

                startPlayback(pending)
            }
        }
    }

    private fun showCompatibilityToast() {
        platform.showToast("Playback may be unstable on this device")
    }

    fun playDespiteWarning(dontShowAgain: Boolean = false) {
        val pending = pendingPlayback ?: return
        _compatibilityWarning.value = null
        if (dontShowAgain) {
            viewModelScope.launch {
                preferences.setShouldShowCompatibilityWarning(false)
            }
        }
        viewModelScope.launch { startPlayback(pending) }
    }

    fun dismissCompatibilityWarning() {
        _compatibilityWarning.value = null
        pendingPlayback = null
        player.stop()
    }

    fun retryPlayback() {
        val pending = pendingPlayback ?: return
        _playbackFailure.value = null
        viewModelScope.launch { startPlayback(pending) }
    }

    fun dismissPlaybackFailure() {
        _playbackFailure.value = null
        player.stop()
    }

    private suspend fun startPlayback(pending: PendingPlayback) {
        val media = pending.media
        _compatibilityWarning.value = null
        _playbackFailure.value = null
        activeMediaId = media.id
        downloadedSubtitleFiles.clear()
        _onlineSubtitles.value = OnlineSubtitleState.Idle
        val progress = repository.getProgress(media.id)

        player.setSubtitleDelay(_subtitleDelayMs.value)
        
        player.setTrackTypeDisabled(TrackTypes.AUDIO, false)
        player.clearTrackOverrides(TrackTypes.AUDIO)
        player.setTrackTypeDisabled(TrackTypes.VIDEO, false)

        val (mediaUri, subtitles) = buildMediaItem(media)
        player.setMedia(mediaUri, subtitles)

        val hasActiveProgress = progress?.let {
            it.duration > 0 &&
                it.position >= it.duration * 0.05 &&
                it.position < it.duration * 0.95
        } == true
        val isBelowResumeThreshold = progress?.let {
            it.duration > 0 && it.position < it.duration * 0.05
        } == true
        val shouldRestart = pending.startFromBeginning || isBelowResumeThreshold ||
            (progress?.isCompleted == true && !hasActiveProgress) ||
            progress?.let { it.duration > 0 && it.position >= it.duration * 0.95 } == true

        val startPosition = if (shouldRestart) 0L else progress?.position ?: 0L
        player.seekTo(startPosition)
        // Starting playback establishes the latest viewing session immediately.
        // A rewatch must not retain completion from the previous viewing.
        repository.saveProgress(
            PlaybackProgressEntity(
                mediaId = media.id,
                position = startPosition,
                duration = progress?.duration?.takeIf { it > 0 } ?: media.duration ?: 0L,
                lastUpdated = System.currentTimeMillis(),
                isCompleted = false,
                completedAt = null,
                keepInContinueWatching = media.type == "EPISODE"
            )
        )

        if (media.type == "EPISODE" && media.seasonNumber != null && media.episodeNumber != null) {
            _nextEpisode.value = repository.getEpisodesForShow(media.title)
                .filter { episode ->
                    val season = episode.seasonNumber
                    val number = episode.episodeNumber
                    season != null && number != null &&
                        (season > media.seasonNumber ||
                            (season == media.seasonNumber && number > media.episodeNumber))
                }
                .minWithOrNull(compareBy<MediaEntity>({ it.seasonNumber }, { it.episodeNumber }, { it.filePath }))
        }

        player.play()
        startProgressTracker(media.id)
        resetOsdTimer()
    }

    fun playNextEpisode() {
        val next = _nextEpisode.value ?: return
        // Claim the transition before saving so Enter and playback-ended cannot
        // enqueue the same episode twice.
        _nextEpisode.value = null
        progressTrackerJob?.cancel()
        val previousId = activeMediaId
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration
        player.pause()
        hideOsd()
        viewModelScope.launch {
            if (previousId != null) saveProgressSnapshot(previousId, position, duration, true)
            loadMedia(next.id, startFromBeginning = true)
        }
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        setSubtitleDelay(_subtitleDelayMs.value + deltaMs)
    }

    fun resetSubtitleDelay() {
        setSubtitleDelay(0L)
    }

    private fun setSubtitleDelay(value: Long) {
        val adjusted = value.coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS)
        _subtitleDelayMs.value = adjusted
        player.setSubtitleDelay(adjusted)
        persistSyncSettings()
    }

    private fun restoreSyncSettings(mediaId: Long) {
        val subtitleDelay = syncPreferences.getLong(
            subtitleDelayKey(mediaId),
            0L
        ).coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS)
        _subtitleDelayMs.value = subtitleDelay
        player.setSubtitleDelay(subtitleDelay)
    }

    private fun persistSyncSettings() {
        val mediaId = _media.value?.id ?: return
        syncPreferences.edit()
            .putLong(subtitleDelayKey(mediaId), _subtitleDelayMs.value)
            .apply()
    }

    private suspend fun buildMediaItem(media: MediaEntity): Pair<String, List<ExternalSubtitle>> {
        val isDocumentUri = media.filePath.startsWith("content://")
        val videoFile = media.filePath.takeUnless { isDocumentUri }?.let(::File)
        val videoBaseName = videoFile?.nameWithoutExtension ?: platform.baseName(media.filePath)
        val localSubtitles = if (isDocumentUri) {
            withContext(Dispatchers.IO) {
                platform.siblingDocumentSubtitles(media, videoBaseName, SUBTITLE_EXTENSIONS)
            }
        } else {
            videoFile?.let(::findSiblingSubtitles).orEmpty().map { file ->
                SubtitleFile(platform.fileUri(file), file.nameWithoutExtension, file.extension)
            }
        }
        val downloadedSubtitles = downloadedSubtitleFiles.map { file ->
            SubtitleFile(platform.fileUri(file), file.nameWithoutExtension, file.extension)
        }
        val subtitles = (localSubtitles + downloadedSubtitles)
            .distinctBy { it.uri }
            .mapIndexed { index, subtitle ->
                ExternalSubtitle(
                    uri = subtitle.uri,
                    mimeType = subtitleMimeType(subtitle.extension),
                    label = subtitle.label,
                    language = inferLanguage(subtitle.label, videoBaseName),
                    isDefault = index == 0
                )
            }
        return platform.playbackUri(media.filePath) to subtitles
    }

    private fun findSiblingSubtitles(videoFile: File): List<File> {
        val base = videoFile.nameWithoutExtension
        return runCatching {
            videoFile.parentFile?.listFiles().orEmpty().filter { candidate ->
                candidate.isFile && candidate.extension.lowercase() in SUBTITLE_EXTENSIONS &&
                    (candidate.nameWithoutExtension.equals(base, ignoreCase = true) ||
                        candidate.nameWithoutExtension.startsWith("$base.", ignoreCase = true))
            }
        }.getOrDefault(emptyList())
    }

    fun searchOpenSubtitles() {
        val media = _media.value ?: return
        if (AppBuild.OPENSUBTITLES_API_KEY.isBlank()) {
            _onlineSubtitles.value = OnlineSubtitleState.Error(
                "Add OPENSUBTITLES_API_KEY to local.properties to enable online subtitles."
            )
            return
        }
        val token = openSubtitlesSessionManager.tokenOrNull()
        if (token == null) {
            _onlineSubtitles.value = OnlineSubtitleState.Error(
                "Sign in to OpenSubtitles.com from Settings first."
            )
            return
        }
        _onlineSubtitles.value = OnlineSubtitleState.Loading
        viewModelScope.launch {
            _onlineSubtitles.value = runCatching {
                val response = openSubtitlesApi.searchSubtitles(
                    url = openSubtitlesSessionManager.searchEndpoint(),
                    tmdbId = media.tmdbId,
                    query = media.title,
                    seasonNumber = media.seasonNumber,
                    episodeNumber = media.episodeNumber,
                    apiKey = AppBuild.OPENSUBTITLES_API_KEY,
                    authorization = "Bearer $token"
                )
                val options = response.data.mapNotNull(::toOnlineSubtitleOption).take(20)
                OnlineSubtitleState.Results(options)
            }.getOrElse {
                handleExpiredOpenSubtitlesSession(it)
                OnlineSubtitleState.Error(it.message ?: "Subtitle search failed")
            }
        }
    }

    fun downloadOpenSubtitle(option: OnlineSubtitleOption) {
        val media = _media.value ?: return
        val token = openSubtitlesSessionManager.tokenOrNull()
        if (token == null) {
            _onlineSubtitles.value = OnlineSubtitleState.Error(
                "Your OpenSubtitles session expired. Sign in again from Settings."
            )
            return
        }
        _onlineSubtitles.value = OnlineSubtitleState.Downloading(option.label)
        viewModelScope.launch {
            val result: OnlineSubtitleState = runCatching<OnlineSubtitleState> {
                val download = openSubtitlesApi.createDownload(
                    openSubtitlesSessionManager.downloadEndpoint(),
                    OpenSubtitlesDownloadRequest(option.fileId),
                    AppBuild.OPENSUBTITLES_API_KEY,
                    "Bearer $token"
                )
                val extension = download.fileName?.substringAfterLast('.', "srt") ?: "srt"
                val directory = File(platform.cacheDir, "open_subtitles").apply { mkdirs() }
                val destination = File(directory, "${media.id}-${option.fileId}.$extension")
                withContext(Dispatchers.IO) {
                    okHttpClient.newCall(Request.Builder().url(download.link).build()).execute().use { response ->
                        check(response.isSuccessful) { "Subtitle download returned ${response.code}" }
                        response.body?.byteStream()?.use { input ->
                            destination.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("Subtitle download was empty")
                    }
                }
                downloadedSubtitleFiles += destination
                val position = player.currentPosition
                val isPlaying = player.isPlaying.value
                val (mediaUri, subtitles) = buildMediaItem(media)
                player.setMedia(mediaUri, subtitles)
                player.seekTo(position)
                if (isPlaying) player.play()
                OnlineSubtitleState.Attached(option.label)
            }.getOrElse {
                handleExpiredOpenSubtitlesSession(it)
                OnlineSubtitleState.Error(it.message ?: "Subtitle download failed")
            }
            _onlineSubtitles.value = result
        }
    }

    private fun handleExpiredOpenSubtitlesSession(error: Throwable) {
        val code = (error as? retrofit2.HttpException)?.code()
        if (code == 401 || code == 406) openSubtitlesSessionManager.logout()
    }

    private fun startProgressTracker(mediaId: Long) {
        progressTrackerJob?.cancel()
        progressTrackerJob = viewModelScope.launch {
            while (true) {
                if (player.isPlaying.value) {
                    saveProgressSnapshot(mediaId, player.currentPosition, player.duration, false)
                }
                delay(5000)
            }
        }
    }

    fun saveProgressNow(markCompleted: Boolean = false) {
        if (markCompleted) progressTrackerJob?.cancel()
        val mediaId = activeMediaId ?: return
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration
        viewModelScope.launch { saveProgressSnapshot(mediaId, position, duration, markCompleted) }
    }

    private suspend fun saveProgressSnapshot(mediaId: Long, position: Long, duration: Long, markCompleted: Boolean) {
        val existing = repository.getProgress(mediaId)
        val safeDuration = duration.takeIf { it > 0 } ?: existing?.duration ?: 0L
        val completedNow = markCompleted || (safeDuration > 0 && position >= safeDuration * 0.95)
        val isEpisode = _media.value?.type == "EPISODE"
        val crossedResumeThreshold = safeDuration > 0 && position >= safeDuration * 0.05
        repository.saveProgress(
            PlaybackProgressEntity(
                mediaId = mediaId,
                position = position,
                duration = safeDuration,
                lastUpdated = System.currentTimeMillis(),
                isCompleted = completedNow,
                completedAt = if (completedNow) System.currentTimeMillis() else null,
                keepInContinueWatching = !completedNow && isEpisode &&
                    (existing?.keepInContinueWatching == true || existing?.isCompleted == true || crossedResumeThreshold)
            )
        )
    }

    fun toggleOsd() {
        _isOsdVisible.value = !_isOsdVisible.value
        if (_isOsdVisible.value) resetOsdTimer()
    }
    fun hideOsd() { osdTimerJob?.cancel(); _isOsdVisible.value = false }
    fun showOsdBriefly() { _isOsdVisible.value = true; resetOsdTimer() }
    fun setScrubbing(scrubbing: Boolean) {
        if (scrubbing) {
            osdTimerJob?.cancel()
        } else {
            resetOsdTimer()
        }
    }
    private fun resetOsdTimer() {
        osdTimerJob?.cancel()
        if (!player.isPlaying.value) return

        osdTimerJob = viewModelScope.launch {
            delay(3000)
            if (player.isPlaying.value) {
                _isOsdVisible.value = false
            }
        }
    }
    fun seekForward() {
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + 10000).coerceAtMost(duration)); showOsdBriefly()
    }
    fun seekBackward() { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)); showOsdBriefly() }

    override fun onCleared() {
        companionHub.onPlaybackMedia(null)
        player.removeListener(playerListener)
        player.stop()
        player.release()
        progressTrackerJob?.cancel()
        osdTimerJob?.cancel()
        super.onCleared()
    }

    private fun subtitleDelayKey(mediaId: Long) = "subtitle_delay_$mediaId"
    companion object {
        const val SYNC_STEP_MS = 100L
        const val MAX_SUBTITLE_DELAY_MS = 5_000L
        private const val PLAYBACK_SYNC_PREFERENCES = "playback_sync"
    }
}

data class PlaybackCompatibilityWarning(
    val hasDolbyVision: Boolean,
    val hasEac3: Boolean,
    val hasTrueHd: Boolean = false,
    val is4kHevc: Boolean = false,
    val hasHdr10: Boolean = false,
    val is10Bit: Boolean = false
) {
    val title: String
        get() = when {
            hasDolbyVision -> "Dolby Vision may not be supported"
            hasTrueHd -> "High-quality audio warning"
            is4kHevc -> "4K HEVC Playback"
            hasHdr10 || is10Bit -> "10-bit HDR Video detected"
            else -> "Playback may be unstable"
        }

    val message: String
        get() = when {
            hasDolbyVision && hasEac3 ->
                "This video uses Dolby Vision and E-AC-3 audio. Your device may not support these, leading to discolouring (purple/green tints) or lag."
            hasDolbyVision ->
                "This video uses Dolby Vision. If your device doesn't support the specific profile, you may see discoloured video (purple/green tints)."
            hasTrueHd ->
                "This video uses Dolby TrueHD audio. This often requires software decoding which is very CPU-intensive and can cause lag on 4K files."
            is10Bit && !hasHdr10 ->
                "This is a 10-bit HEVC file. Many older devices only support 8-bit hardware decoding. If this video flickers green, try disabling 'Experimental Tunneling' in Settings."
            is4kHevc && hasHdr10 ->
                "This is a 4K HDR10 file. If the device hardware decoder is unstable, it may fall back to software decoding, causing severe lag and washed-out colors."
            hasHdr10 ->
                "This is an HDR10 file. If your device hardware struggles, you may see flickering or green tints. Try disabling 'Experimental Tunneling' in Settings if issues occur."
            is4kHevc ->
                "This is a 4K HEVC file. If playback is laggy, it may be due to the device struggling with hardware decoding or falling back to software."
            else ->
                "This video uses E-AC-3 audio, which this device may not decode correctly. Continuing could cause flickering, stuttering, or missing audio."
        }

    val proceedLabel: String
        get() = if (hasDolbyVision || is4kHevc || hasTrueHd || hasHdr10 || is10Bit) "Try Anyway" else "Watch Anyway"
}

data class PlaybackFailure(
    val title: String,
    val message: String
)

private data class PendingPlayback(
    val media: MediaEntity,
    val startFromBeginning: Boolean,
    val compatibility: PlaybackCompatibilityWarning?
)

data class PlayerSubtitleStyle(
    val textScale: Float = 1f,
    val colour: String = "white",
    val backgroundOpacity: Float = 0.55f
)

data class OnlineSubtitleOption(val fileId: Int, val label: String, val language: String?)

sealed interface OnlineSubtitleState {
    data object Idle : OnlineSubtitleState
    data object Loading : OnlineSubtitleState
    data class Results(val options: List<OnlineSubtitleOption>) : OnlineSubtitleState
    data class Downloading(val label: String) : OnlineSubtitleState
    data class Attached(val label: String) : OnlineSubtitleState
    data class Error(val message: String) : OnlineSubtitleState
}

private fun toOnlineSubtitleOption(result: OpenSubtitleResult): OnlineSubtitleOption? {
    val file = result.attributes.files.firstOrNull() ?: return null
    val language = result.attributes.language
    val hearingImpaired = if (result.attributes.hearingImpaired) " · HI" else ""
    return OnlineSubtitleOption(
        fileId = file.fileId,
        label = listOfNotNull(language?.uppercase(), result.attributes.release ?: file.fileName)
            .joinToString(" · ") + hearingImpaired,
        language = language
    )
}

private fun subtitleMimeType(extension: String): String = when (extension.lowercase()) {
    "srt" -> "application/x-subrip"
    "vtt" -> "text/vtt"
    "ssa", "ass" -> "text/x-ssa"
    "ttml", "dfxp", "xml" -> "application/ttml+xml"
    else -> "application/x-subrip"
}

private fun inferLanguage(subtitleName: String, videoName: String): String? =
    subtitleName.removePrefix(videoName).trimStart('.', ' ', '-', '_')
        .substringBefore('.').takeIf { it.length in 2..3 }

internal val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ssa", "ass", "ttml", "dfxp", "xml")
