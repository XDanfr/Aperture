package me.xdan.aperture.ui.screen.mylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.xdan.aperture.data.jellyfin.JellyfinCatalog
import me.xdan.aperture.domain.catalog.BrowseEntry
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.ProfileRepository

/**
 * My List: local favourites, the Jellyfin server's favourites, or both, by the profile's library
 * mode. Local titles keep the order they were added in; server favourites follow, by title.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyListViewModel constructor(
    repository: MediaRepository,
    catalog: JellyfinCatalog,
    profiles: ProfileRepository,
    hybrid: me.xdan.aperture.domain.hybrid.HybridIndexSource
) : ViewModel() {
    private val remote = catalog.activeAccount.flatMapLatest { account ->
        if (account == null) flowOf(emptyList())
        else catalog.favorites(account).map { items -> items.map { BrowseEntry.Remote(it) } }
    }

    val entries: StateFlow<List<BrowseEntry>> = profiles.activeLibraryMode.flatMapLatest { mode ->
        combine(
            if (mode.usesLocal) repository.getFavoriteMedia().map { list -> list.map { BrowseEntry.Local(it) } } else flowOf(emptyList()),
            if (mode.usesJellyfin) remote else flowOf(emptyList()),
            hybrid.index
        ) { local, server, index ->
            // A server favourite that's also a local favourite shows once.
            val localIds = local.mapNotNull { (it as? BrowseEntry.Local)?.media?.id }.toSet()
            val covered = localIds.flatMap { index.remoteFor[it].orEmpty() }.map { it.itemId }.toSet()
            local + server.filterNot { entry ->
                entry is BrowseEntry.Remote && (entry.item.key as? me.xdan.aperture.domain.model.MediaKey.Jellyfin)?.itemId in covered
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
