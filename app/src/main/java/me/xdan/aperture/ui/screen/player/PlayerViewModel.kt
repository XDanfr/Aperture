@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _media = MutableStateFlow<MediaEntity?>(null)
    val media: StateFlow<MediaEntity?> = _media
    private val _isOsdVisible = MutableStateFlow(true)
    val isOsdVisible: StateFlow<Boolean> = _isOsdVisible
    private val _onlineSubtitles = MutableStateFlow<OnlineSubtitleState>(OnlineSubtitleState.Idle)
    val onlineSubtitles: StateFlow<OnlineSubtitleState> = _onlineSubtitles

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

    fun loadMedia(mediaId: Long, startFromBeginning: Boolean = false) {
        viewModelScope.launch {
            val mediaEntity = repository.getMediaById(mediaId)
            _media.value = mediaEntity
            mediaEntity?.let { media ->
                activeMediaId = media.id
                downloadedSubtitleFiles.clear()
                _onlineSubtitles.value = OnlineSubtitleState.Idle
                val progress = repository.getProgress(media.id)
                // Track overrides belong to the previous MediaItem. In
                // particular, forcing an unsupported audio track can otherwise
                // leave this singleton player stuck at 00:00 for every file
                // opened afterwards.
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .build()
                player.setMediaItem(buildMediaItem(media))
                player.prepare()
                val hasActiveProgress = progress?.let {
                    it.duration > 0 && it.position >= it.duration * 0.05 && it.position < it.duration * 0.95
                } == true
                val isBelowResumeThreshold = progress?.let {
                    it.duration > 0 && it.position < it.duration * 0.05
                } == true
                val shouldRestart = startFromBeginning || isBelowResumeThreshold ||
                    (progress?.isCompleted == true && !hasActiveProgress) ||
                    progress?.let { it.duration > 0 && it.position >= it.duration * 0.95 } == true
                if (shouldRestart) {
                    player.seekTo(0)
                    progress?.let {
                        repository.saveProgress(it.copy(position = 0L, lastUpdated = System.currentTimeMillis()))
                    }
                } else progress?.let { player.seekTo(it.position) }
                player.playWhenReady = true
                startProgressTracker(media.id)
                resetOsdTimer()
            }
        }
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
        _onlineSubtitles.value = OnlineSubtitleState.Loading
        viewModelScope.launch {
            _onlineSubtitles.value = runCatching {
                val response = openSubtitlesApi.searchSubtitles(
                    tmdbId = media.tmdbId,
                    query = media.title,
                    seasonNumber = media.seasonNumber,
                    episodeNumber = media.episodeNumber,
                    apiKey = BuildConfig.OPENSUBTITLES_API_KEY
                )
                val options = response.data.mapNotNull(::toOnlineSubtitleOption).take(20)
                OnlineSubtitleState.Results(options)
            }.getOrElse { OnlineSubtitleState.Error(it.message ?: "Subtitle search failed") }
        }
    }

    fun downloadOpenSubtitle(option: OnlineSubtitleOption) {
        val media = _media.value ?: return
        _onlineSubtitles.value = OnlineSubtitleState.Downloading(option.label)
        viewModelScope.launch {
            val result: OnlineSubtitleState = runCatching<OnlineSubtitleState> {
                val download = openSubtitlesApi.createDownload(
                    OpenSubtitlesDownloadRequest(option.fileId),
                    BuildConfig.OPENSUBTITLES_API_KEY
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
            }.getOrElse { OnlineSubtitleState.Error(it.message ?: "Subtitle download failed") }
            _onlineSubtitles.value = result
        }
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
        super.onCleared()
        player.stop(); progressTrackerJob?.cancel(); osdTimerJob?.cancel()
    }
}

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
