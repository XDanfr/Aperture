package me.xdan.aperture.ui.screen.jellyfin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.xdan.aperture.data.jellyfin.JellyfinCatalog
import me.xdan.aperture.domain.catalog.CatalogItem
import me.xdan.aperture.domain.catalog.LibraryQuery
import me.xdan.aperture.domain.catalog.LibraryQueryStore
import me.xdan.aperture.domain.catalog.letterIndex
import me.xdan.aperture.domain.repository.ProfileRepository
import me.xdan.aperture.ui.component.LibraryOptions

/** One server library as a grid: sorted, filtered and remembered per profile. */
@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinLibraryViewModel(
    private val libraryId: String,
    private val catalog: JellyfinCatalog,
    private val profiles: ProfileRepository,
    private val queries: LibraryQueryStore
) : ViewModel() {

    data class State(
        val name: String = "",
        val items: List<CatalogItem>? = null,
        val query: LibraryQuery = LibraryQuery(),
        /** First index for each letter, when sorted by title. */
        val letters: Map<Char, Int> = emptyMap(),
        val options: LibraryOptions = LibraryOptions()
    )

    private val query = MutableStateFlow(LibraryQuery())
    /** Reseeded each time "Random" is picked, so the order only changes when asked to. */
    private val seed = MutableStateFlow((System.currentTimeMillis() % 65_536).toInt())
    private var profileId = 0L

    init {
        viewModelScope.launch {
            profileId = profiles.activeProfileId.first()
            query.value = queries.load(profileId, libraryId)
        }
    }

    private val items = combine(catalog.activeAccount, query, seed) { account, q, s -> Triple(account, q, s) }
        .flatMapLatest { (account, q, s) ->
            if (account == null) flowOf(emptyList()) else catalog.browse(account, libraryId, q, s)
        }

    /** Genres and decades come from the whole library, so picking one doesn't hide the others. */
    private val options = combine(catalog.activeAccount, catalog.libraries) { account, libraries ->
        account to libraries.firstOrNull { it.id == libraryId }?.collectionType
    }.flatMapLatest { (account, type) ->
        if (account == null) flowOf(LibraryOptions())
        else catalog.browse(account, libraryId, LibraryQuery(), 0).map { all ->
            LibraryOptions(
                genres = all.flatMap { it.genres }.distinct().sortedBy { it.lowercase() },
                decades = all.mapNotNull { it.year?.let { year -> year / 10 * 10 } }.distinct().sortedDescending(),
                // Shows have no single file to measure.
                offerUhd = type != "tvshows"
            )
        }
    }

    val state: StateFlow<State> = combine(catalog.libraries, items, query, options) { libraries, list, q, opts ->
        State(
            name = libraries.firstOrNull { it.id == libraryId }?.name.orEmpty(),
            items = list,
            query = q,
            letters = if (q.sort == LibraryQuery.Sort.TITLE) letterIndex(list.map { it.title }, q.descending) else emptyMap(),
            options = opts
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun update(transform: (LibraryQuery) -> LibraryQuery) {
        val updated = transform(query.value)
        if (updated.sort == LibraryQuery.Sort.RANDOM) seed.value = (System.nanoTime() % 65_536).toInt()
        query.value = updated
        queries.save(profileId, libraryId, updated)
    }

    /** Picking the current sort again flips its direction. */
    fun sortBy(sort: LibraryQuery.Sort) = update { q ->
        if (q.sort == sort) q.copy(descending = !q.descending) else q.copy(sort = sort, descending = sort.defaultDescending)
    }
}
