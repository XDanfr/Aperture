package me.xdan.aperture.ui.screen.jellyfin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import me.xdan.aperture.domain.repository.ProfileRepository

/** One server library as a grid: sorted, filtered and remembered per profile. */
class JellyfinLibraryViewModel(
    private val libraryId: String,
    private val catalog: JellyfinCatalog,
    private val profiles: ProfileRepository
) : ViewModel() {

    data class State(
        val name: String = "",
        val items: List<CatalogItem>? = null,
        val query: LibraryQuery = LibraryQuery(),
        /** First index for each letter, when sorted by title. "#" covers digits and symbols. */
        val letters: Map<Char, Int> = emptyMap(),
        val genres: List<String> = emptyList()
    )

    private val query = MutableStateFlow(LibraryQuery())
    /** Reseeded each time "Random" is picked, so the order only changes when asked to. */
    private val seed = MutableStateFlow((System.currentTimeMillis() % 65_536).toInt())
    private var profileId = 0L

    init {
        viewModelScope.launch {
            profileId = profiles.activeProfileId.first()
            query.value = catalog.savedQuery(profileId, libraryId)
        }
    }

    private val items = combine(catalog.activeAccount, query, seed) { account, q, s -> Triple(account, q, s) }
        .flatMapLatest { (account, q, s) ->
            if (account == null) flowOf(emptyList()) else catalog.browse(account, libraryId, q, s)
        }

    /** Genres come from the unfiltered library, so picking one doesn't hide the others. */
    private val genres = catalog.activeAccount.flatMapLatest { account ->
        if (account == null) flowOf(emptyList())
        else catalog.browse(account, libraryId, LibraryQuery(), 0)
            .map { all -> all.flatMap { it.genres }.distinct().sortedBy { it.lowercase() } }
    }

    val state: StateFlow<State> = combine(catalog.libraries, items, query, genres) { libraries, list, q, allGenres ->
        State(
            name = libraries.firstOrNull { it.id == libraryId }?.name.orEmpty(),
            items = list,
            query = q,
            letters = if (q.sort == LibraryQuery.Sort.TITLE) letterIndex(list, q.descending) else emptyMap(),
            genres = allGenres
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun update(transform: (LibraryQuery) -> LibraryQuery) {
        val updated = transform(query.value)
        if (updated.sort == LibraryQuery.Sort.RANDOM) seed.value = (System.nanoTime() % 65_536).toInt()
        query.value = updated
        catalog.saveQuery(profileId, libraryId, updated)
    }

    /** Picking the current sort again flips its direction. */
    fun sortBy(sort: LibraryQuery.Sort) = update { q ->
        if (q.sort == sort) q.copy(descending = !q.descending) else q.copy(sort = sort, descending = sort.defaultDescending)
    }

    private fun letterIndex(items: List<CatalogItem>, descending: Boolean): Map<Char, Int> {
        val index = LinkedHashMap<Char, Int>()
        items.forEachIndexed { position, item ->
            val letter = sortLetter(item.title)
            if (letter !in index) index[letter] = position
        }
        return if (descending) index else index.toSortedMap(compareBy { if (it == '#') ' ' else it })
    }

    companion object {
        /** The letter a title files under, ignoring a leading "The"/"A"/"An" like Jellyfin's sort names. */
        fun sortLetter(title: String): Char {
            val trimmed = title.trim().replace(Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE), "")
            val first = trimmed.firstOrNull()?.uppercaseChar() ?: return '#'
            return if (first in 'A'..'Z') first else '#'
        }
    }
}
