package me.xdan.aperture.ui.screen.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.domain.catalog.LibraryQuery
import me.xdan.aperture.domain.catalog.LibraryQueryStore
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.ProfileRepository
import me.xdan.aperture.domain.repository.UserPreferencesRepository

class LibraryViewModel constructor(
    repository: MediaRepository,
    preferences: UserPreferencesRepository,
    profiles: ProfileRepository,
    private val queries: LibraryQueryStore
) : ViewModel() {
    private val content = LibraryContent(
        repository.getMediaByType("MOVIE"),
        repository.getMediaByType("EPISODE"),
        viewModelScope
    )
    val movies = content.movies
    val showGroups = content.showGroups

    val showPresentationMode = preferences.showPresentationMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "grouped")

    private val movieQuery = MutableStateFlow(LibraryQuery())
    private val showQuery = MutableStateFlow(LibraryQuery())
    /** Reseeded each time "Random" is picked, so the order only changes when asked to. */
    private val seed = MutableStateFlow((System.currentTimeMillis() % 65_536).toInt())
    private var profileId = 0L

    init {
        viewModelScope.launch {
            profileId = profiles.activeProfileId.first()
            movieQuery.value = queries.load(profileId, MOVIES)
            showQuery.value = queries.load(profileId, SHOWS)
        }
    }

    private val progress = repository.getAllProgress().map { list -> list.associateBy { it.mediaId } }

    val movieGrid: StateFlow<LocalGrid<MediaEntity>> = combine(movies, progress, movieQuery, seed) { all, p, q, s ->
        LocalLibraryQuery.movies(all, p, q, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalGrid())

    val showGrid: StateFlow<LocalGrid<ShowGroup>> = combine(showGroups, progress, showQuery, seed) { all, p, q, s ->
        LocalLibraryQuery.shows(all, p, q, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalGrid())

    fun updateMovies(transform: (LibraryQuery) -> LibraryQuery) = update(movieQuery, MOVIES, transform)
    fun updateShows(transform: (LibraryQuery) -> LibraryQuery) = update(showQuery, SHOWS, transform)
    fun sortMovies(sort: LibraryQuery.Sort) = updateMovies { it.toggled(sort) }
    fun sortShows(sort: LibraryQuery.Sort) = updateShows { it.toggled(sort) }

    private fun update(state: MutableStateFlow<LibraryQuery>, id: String, transform: (LibraryQuery) -> LibraryQuery) {
        val updated = transform(state.value)
        if (updated.sort == LibraryQuery.Sort.RANDOM) seed.value = (System.nanoTime() % 65_536).toInt()
        state.value = updated
        queries.save(profileId, id, updated)
    }

    /** Picking the current sort again flips its direction. */
    private fun LibraryQuery.toggled(sort: LibraryQuery.Sort) =
        if (this.sort == sort) copy(descending = !descending) else copy(sort = sort, descending = sort.defaultDescending)

    private companion object {
        const val MOVIES = "local:movies"
        const val SHOWS = "local:shows"
    }
}

data class ShowGroup(
    val title: String,
    val episodes: List<MediaEntity>
) {
    val representative: MediaEntity =
        // a show card must open at the first available episode, not whichever episode happened to receive poster art first.
        episodes.minWithOrNull(
            compareBy<MediaEntity>(
                { it.seasonNumber ?: Int.MAX_VALUE },
                { it.episodeNumber ?: Int.MAX_VALUE },
                { it.id }
            )
        ) ?: episodes.first()

    val seasons: List<ShowSeason> = episodes.groupBy { it.seasonNumber ?: 0 }
        .toSortedMap()
        .map { (number, episodes) -> ShowSeason(number, episodes) }
}

data class ShowSeason(val number: Int, val episodes: List<MediaEntity>)
