@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.xdan.aperture.BuildConfig
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
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val player: ExoPlayer,
    private val repository: MediaRepository,
    preferences: UserPreferencesRepository,
    private val openSubtitlesApi: OpenSubtitlesApi,
    private val openSubtitlesSessionManager: OpenSubtitlesSessionManager,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _media = MutableStateFlow<MediaEntity?>(null)
    val media: StateFlow<MediaEntity?> = _media
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

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            progressTrackerJob?.cancel()
            osdTimerJob?.cancel()
            _isOsdVisible.value = false

            val compatibility = pendingPlayback?.compatibility
            _playbackFailure.value = when {
                compatibility?.hasDolbyVision == true -> PlaybackFailure(
                    title = "This video could not be decoded",
                    message = "The device failed to play this Dolby Vision video. Try a non-Dolby Vision version, such as standard HEVC or H.264."
                )
                error.errorCodeName.contains("DECODING") -> PlaybackFailure(
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
    }

    fun loadMedia(mediaId: Long, startFromBeginning: Boolean = false) {
        viewModelScope.launch {
            val mediaEntity = repository.getMediaById(mediaId)
            _media.value = mediaEntity
            _compatibilityWarning.value = null
            _playbackFailure.value = null

            mediaEntity?.let { media ->
                val compatibility = withContext(Dispatchers.IO) {
                    inspectPlaybackCompatibility(media.filePath)
                }
                val pending = PendingPlayback(media, startFromBeginning, compatibility)
                pendingPlayback = pending

                if (compatibility != null) {
                    player.stop()
                    _compatibilityWarning.value = compatibility
                    return@launch
                }

                startPlayback(pending)
            }
        }
    }

    fun playDespiteWarning() {
        val pending = pendingPlayback ?: return
        _compatibilityWarning.value = null
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

        player.stop()
        player.clearMediaItems()

        // Track overrides belong to the previous MediaItem. In particular,
        // forcing an unsupported audio track can otherwise leave this singleton
        // player stuck at 00:00 for every file opened afterwards.
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .build()

        player.setMediaItem(buildMediaItem(media))
        player.prepare()

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
        startProgressTracker(media.id)
        resetOsdTimer()
    }

    private fun buildMediaItem(media: MediaEntity): MediaItem {
        val videoFile = File(media.filePath)
        val subtitles = (findSiblingSubtitles(videoFile) + downloadedSubtitleFiles)
            .distinctBy { it.absolutePath }
            .mapIndexed { index, file ->
                MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                    .setMimeType(subtitleMimeType(file.extension))
                    .setLabel(file.nameWithoutExtension)
                    .setLanguage(inferLanguage(file.nameWithoutExtension, videoFile.nameWithoutExtension))
                    .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
        return MediaItem.Builder()
            .setUri(Uri.fromFile(videoFile))
            .setSubtitleConfigurations(subtitles)
            .build()
    }

    private fun inspectPlaybackCompatibility(filePath: String): PlaybackCompatibilityWarning? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(filePath)
            var hasDolbyVision = false
            var hasEac3 = false

            repeat(extractor.trackCount) { index ->
                val mime = extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.lowercase()
                    ?: return@repeat

                when (mime) {
                    "video/dolby-vision" -> hasDolbyVision = true
                    "audio/eac3", "audio/eac3-joc" -> hasEac3 = true
                }
            }

            PlaybackCompatibilityWarning(
                hasDolbyVision = hasDolbyVision,
                hasEac3 = hasEac3
            ).takeIf { it.hasDolbyVision || it.hasEac3 }
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
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
        if (BuildConfig.OPENSUBTITLES_API_KEY.isBlank()) {
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
                    apiKey = BuildConfig.OPENSUBTITLES_API_KEY,
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
                    BuildConfig.OPENSUBTITLES_API_KEY,
                    "Bearer $token"
                )
                val extension = download.fileName?.substringAfterLast('.', "srt") ?: "srt"
                val directory = File(context.cacheDir, "open_subtitles").apply { mkdirs() }
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
                val playWhenReady = player.playWhenReady
                player.setMediaItem(buildMediaItem(media), position)
                player.prepare()
                player.playWhenReady = playWhenReady
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
                if (player.isPlaying) {
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
                isCompleted = existing?.isCompleted == true || completedNow,
                completedAt = if (completedNow) System.currentTimeMillis() else existing?.completedAt,
                keepInContinueWatching = !completedNow && isEpisode &&
                    (existing?.keepInContinueWatching == true || crossedResumeThreshold)
            )
        )
    }

    fun toggleOsd() {
        _isOsdVisible.value = !_isOsdVisible.value
        if (_isOsdVisible.value) resetOsdTimer()
    }
    fun hideOsd() { osdTimerJob?.cancel(); _isOsdVisible.value = false }
    fun showOsdBriefly() { _isOsdVisible.value = true; resetOsdTimer() }
    private fun resetOsdTimer() {
        osdTimerJob?.cancel()
        osdTimerJob = viewModelScope.launch { delay(3000); _isOsdVisible.value = false }
    }
    fun seekForward() {
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + 10000).coerceAtMost(duration)); showOsdBriefly()
    }
    fun seekBackward() { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)); showOsdBriefly() }

    override fun onCleared() {
        player.removeListener(playerListener)
        player.stop()
        progressTrackerJob?.cancel()
        osdTimerJob?.cancel()
        super.onCleared()
    }
}

data class PlaybackCompatibilityWarning(
    val hasDolbyVision: Boolean,
    val hasEac3: Boolean
) {
    val title: String
        get() = if (hasDolbyVision && !hasEac3) {
            "Dolby Vision may not be supported"
        } else {
            "Playback may be unstable"
        }

    val message: String
        get() = when {
            hasDolbyVision && hasEac3 ->
                "This video uses Dolby Vision video and E-AC-3 audio, which this device may not decode correctly. Playback could fail, flicker, stutter, or have missing audio."
            hasDolbyVision ->
                "This video uses Dolby Vision, which this device may not decode correctly. Playback could fail, flicker, or stutter."
            else ->
                "This video uses E-AC-3 audio, which this device may not decode correctly. Continuing could cause flickering, stuttering, or missing audio."
        }

    val proceedLabel: String
        get() = if (hasDolbyVision) "Try Anyway" else "Watch Anyway"
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

private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ssa", "ass", "ttml", "dfxp", "xml")
