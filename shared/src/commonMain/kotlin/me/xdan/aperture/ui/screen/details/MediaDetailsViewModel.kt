package me.xdan.aperture.ui.screen.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.domain.playback.nextEpisodeForContinueWatching
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.data.remote.dto.TmdbResult

class MediaDetailsViewModel constructor(
    private val repository: MediaRepository,
    private val context: coil3.PlatformContext,
    hybrid: me.xdan.aperture.domain.hybrid.HybridIndexSource
) : ViewModel() {


    /** The Info dialog: null when closed, an empty [MediaInfo] never; [infoLoading] while probing. */
    private val _info = MutableStateFlow<me.xdan.aperture.domain.catalog.MediaInfo?>(null)
    val info: StateFlow<me.xdan.aperture.domain.catalog.MediaInfo?> = _info
    private val _infoOpen = MutableStateFlow(false)
    val infoOpen: StateFlow<Boolean> = _infoOpen

    private val _media = MutableStateFlow<MediaEntity?>(null)
    val media: StateFlow<MediaEntity?> = _media

    /**
     * Where the shown title is, when the profile shows both this device and a server and the
     * server has it too; null otherwise.
     */
    val availability: StateFlow<String?> = kotlinx.coroutines.flow.combine(_media, hybrid.index) { media, index ->
        val id = media?.id ?: return@combine null
        val show = if (media.type == "EPISODE") index.shows.firstOrNull { show -> show.localEpisodes.any { it.id == id } } else null
        when {
            show != null && show.episodeMatches.containsKey(id) -> "On this device · This episode is also on Jellyfin"
            show != null && show.remoteSeries.isNotEmpty() -> "On this device · The show is also on Jellyfin"
            index.remoteFor[id].isNullOrEmpty().not() -> "On this device · Also on Jellyfin"
            else -> null
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)
    private val _progress = MutableStateFlow<PlaybackProgressEntity?>(null)
    val progress: StateFlow<PlaybackProgressEntity?> = _progress
    private val _episodes = MutableStateFlow<List<MediaEntity>>(emptyList())
    val episodes: StateFlow<List<MediaEntity>> = _episodes
    private val _assetCandidates = MutableStateFlow<List<TmdbResult>>(emptyList())
    val assetCandidates: StateFlow<List<TmdbResult>> = _assetCandidates
    private val _isLoadingAssets = MutableStateFlow(false)
    val isLoadingAssets: StateFlow<Boolean> = _isLoadingAssets
    private var assetSearchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.getAllProgress().collect { progress ->
                val mediaId = _media.value?.id ?: return@collect
                _progress.value = progress.firstOrNull { it.mediaId == mediaId }
            }
        }
    }

    fun loadMedia(mediaId: Long, preferActiveEpisode: Boolean = true) {
        viewModelScope.launch {
            val selected = repository.getMediaById(mediaId)
            val showEpisodes = if (selected?.type == "EPISODE") {
                repository.getEpisodesForShow(selected.title)
            } else emptyList()
            _episodes.value = showEpisodes

            val resolved = if (preferActiveEpisode && showEpisodes.isNotEmpty()) {
                val progressMap = repository.getAllProgress().first().associateBy { it.mediaId }
                val currentEpisode = nextEpisodeForContinueWatching(showEpisodes, progressMap)?.first
                currentEpisode?.let { repository.getMediaById(it.id) } ?: selected
            } else selected
            _media.value = resolved
            _progress.value = resolved?.let { repository.getProgress(it.id) }
        }
    }

    fun selectEpisode(mediaId: Long) {
        viewModelScope.launch {
            _media.value = repository.getMediaById(mediaId)
            _progress.value = repository.getProgress(mediaId)
        }
    }

    fun toggleFavorite(mediaId: Long) {
        viewModelScope.launch {
            val current = repository.getMediaById(mediaId) ?: return@launch
            repository.setFavorite(mediaId, !current.isFavorite)
            _media.value = repository.getMediaById(mediaId)
        }
    }

    /** The same toggle as the long-press menu: finished, or back to not started. */
    fun toggleWatched(mediaId: Long) {
        viewModelScope.launch {
            val media = repository.getMediaById(mediaId) ?: return@launch
            val current = repository.getProgress(mediaId)
            if (current?.isCompleted == true) {
                repository.clearProgress(mediaId)
            } else {
                val duration = (current?.duration ?: media.duration ?: 1L).coerceAtLeast(1L)
                val now = System.currentTimeMillis()
                repository.saveProgress(
                    PlaybackProgressEntity(
                        mediaId = mediaId, position = duration, duration = duration,
                        lastUpdated = now, isCompleted = true, completedAt = now
                    )
                )
            }
            _progress.value = repository.getProgress(mediaId)
        }
    }

    /** Reads the file's size and tracks on IO, then shows them. */
    fun loadInfo() {
        val current = _media.value ?: return
        _info.value = null
        _infoOpen.value = true
        viewModelScope.launch {
            val probe = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                me.xdan.aperture.platform.probeLocalMedia(context, current.filePath)
            }
            _info.value = me.xdan.aperture.platform.localMediaInfo(current.filePath, current.duration, current.dateAdded, probe)
        }
    }

    fun dismissInfo() {
        _infoOpen.value = false
        _info.value = null
    }

    fun findAssetCandidates(query: String? = null) {
        val current = _media.value ?: return
        assetSearchJob?.cancel()
        assetSearchJob = viewModelScope.launch {
            _isLoadingAssets.value = true
            _assetCandidates.value = runCatching {
                repository.searchMetadataCandidates(current, query)
            }.getOrDefault(emptyList())
            _isLoadingAssets.value = false
        }
    }

    fun selectAssetCandidate(candidate: TmdbResult) {
        val mediaId = _media.value?.id ?: return
        viewModelScope.launch {
            repository.applyMetadataCandidate(mediaId, candidate)
            _media.value = repository.getMediaById(mediaId)
            _media.value?.takeIf { it.type == "EPISODE" }?.let {
                _episodes.value = repository.getEpisodesForShow(it.title)
            }
        }
    }
}
