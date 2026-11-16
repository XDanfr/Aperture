package me.xdan.aperture.ui.screen.jellyfin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.xdan.aperture.data.jellyfin.JellyfinCatalog
import me.xdan.aperture.domain.catalog.CatalogItem
import me.xdan.aperture.domain.catalog.MediaInfo

/** One Jellyfin title's details panel: the title, a show's episodes, and its actions. */
@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinDetailsViewModel(
    private val itemId: String,
    private val catalog: JellyfinCatalog
) : ViewModel() {

    data class State(
        val item: CatalogItem? = null,
        val episodes: List<CatalogItem> = emptyList(),
        /** For a show, the episode the panel acts on. */
        val episode: CatalogItem? = null
    ) {
        /** What Watched and Info apply to: the chosen episode for a show, otherwise the title. */
        val target: CatalogItem? get() = episode ?: item
    }

    sealed interface InfoState {
        data object Loading : InfoState
        data class Ready(val info: MediaInfo) : InfoState
        data object Failed : InfoState
    }

    private val selectedEpisodeId = MutableStateFlow<String?>(null)
    private val _info = MutableStateFlow<InfoState?>(null)
    val info: StateFlow<InfoState?> = _info.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    /** A short note after an action fails, e.g. the server couldn't be reached. */
    val message: StateFlow<String?> = _message.asStateFlow()

    val state: StateFlow<State> = catalog.activeAccount.flatMapLatest { account ->
        if (account == null) return@flatMapLatest flowOf(State())
        catalog.item(account, itemId).flatMapLatest { item ->
            if (item?.kind != CatalogItem.Kind.SHOW) flowOf(State(item = item))
            else combine(catalog.episodes(account, itemId), selectedEpisodeId) { episodes, selected ->
                State(item, episodes, episodes.firstOrNull { it.jellyfinId == selected } ?: defaultEpisode(episodes))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun selectEpisode(episode: CatalogItem) {
        selectedEpisodeId.value = episode.jellyfinId
    }

    /** My List follows the title (the show, not an episode), like the server's favourites. */
    fun toggleFavorite() = act { account ->
        val item = state.value.item ?: return@act true
        catalog.setFavorite(account, item.jellyfinId, !item.isFavorite)
    }

    fun togglePlayed() = act { account ->
        val target = state.value.target ?: return@act true
        catalog.setPlayed(account, target.jellyfinId, !target.played)
    }

    fun loadInfo() {
        val target = state.value.target ?: return
        _info.value = InfoState.Loading
        viewModelScope.launch {
            val account = catalog.activeAccount.first()
            val libraryName = catalog.libraries.first().firstOrNull { it.id == (state.value.item?.libraryId ?: target.libraryId) }?.name
            val info = account?.let { catalog.mediaInfo(it, target.jellyfinId, libraryName) }
            _info.value = if (info != null) InfoState.Ready(info) else InfoState.Failed
        }
    }

    fun dismissInfo() {
        _info.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun act(block: suspend (me.xdan.aperture.data.local.entity.JellyfinAccountEntity) -> Boolean) {
        viewModelScope.launch {
            val account = catalog.activeAccount.first() ?: return@launch
            if (!block(account)) _message.value = "Couldn't reach the server. Try again in a moment."
        }
    }

    companion object {
        /** The episode in progress, else the first unwatched one after the last watched, else the first. */
        fun defaultEpisode(episodes: List<CatalogItem>): CatalogItem? {
            episodes.firstOrNull { it.progress > 0f }?.let { return it }
            val lastWatched = episodes.indexOfLast { it.played }
            return episodes.drop(lastWatched + 1).firstOrNull { !it.played } ?: episodes.firstOrNull()
        }
    }
}

/** The server's id for a Jellyfin catalog item. */
val CatalogItem.jellyfinId: String
    get() = (key as me.xdan.aperture.domain.model.MediaKey.Jellyfin).itemId
