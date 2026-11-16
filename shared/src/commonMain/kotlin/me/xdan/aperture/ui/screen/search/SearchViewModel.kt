package me.xdan.aperture.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.xdan.aperture.data.jellyfin.JellyfinCatalog
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.domain.catalog.BrowseEntry
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.ProfileRepository

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class SearchViewModel constructor(
    repository: MediaRepository,
    catalog: JellyfinCatalog,
    profiles: ProfileRepository,
    hybrid: me.xdan.aperture.domain.hybrid.HybridIndexSource
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val local: Flow<List<BrowseEntry>> = _query.flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList()) else repository.searchMedia(query)
    }.map { matches ->
        val movies = matches.filter { it.type != "EPISODE" }
        // One result per show: its first episode, which opens the show's panel.
        val shows = matches.filter { it.type == "EPISODE" }
            .groupBy { it.title }
            .values
            .mapNotNull { episodes ->
                episodes.minWithOrNull(
                    compareBy(
                        { it.seasonNumber ?: Int.MAX_VALUE },
                        { it.episodeNumber ?: Int.MAX_VALUE },
                        { it.filePath }
                    )
                )
            }
        (movies + shows).map { BrowseEntry.Local(it) }
    }

    // A short pause after typing, since each keystroke re-queries a large server index.
    private val remote: Flow<List<BrowseEntry>> = combine(_query.debounce(150), catalog.activeAccount) { q, account -> q to account }
        .flatMapLatest { (query, account) ->
            if (query.isBlank() || account == null) flowOf(emptyList())
            else catalog.search(account, query).map { items -> items.map { BrowseEntry.Remote(it) } }
        }

    val results: StateFlow<List<BrowseEntry>> = profiles.activeLibraryMode.flatMapLatest { mode ->
        combine(
            if (mode.usesLocal) local else flowOf(emptyList()),
            if (mode.usesJellyfin) remote else flowOf(emptyList()),
            hybrid.index
        ) { localResults, remoteResults, index ->
            // A title in both places shows once, as the local copy.
            val merged = remoteResults.filterNot { it is BrowseEntry.Remote && it.item.jellyfinItemId in index.matchedRemoteIds }
            (localResults + merged).sortedBy { it.sortTitle.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }
}

private val me.xdan.aperture.domain.catalog.CatalogItem.jellyfinItemId: String?
    get() = (key as? me.xdan.aperture.domain.model.MediaKey.Jellyfin)?.itemId
