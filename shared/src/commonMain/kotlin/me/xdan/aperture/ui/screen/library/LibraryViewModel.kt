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
    private val queries: LibraryQueryStore,
    hybrid: me.xdan.aperture.domain.hybrid.HybridIndexSource
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

    /**
     * This device's films, plus in hybrid mode the server's films the device doesn't have. A
     * title in both places shows once, as the local copy.
     */
    val movieGrid: StateFlow<LocalGrid<GridEntry<MediaEntity>>> = combine(movies, progress, movieQuery, seed, hybrid.index) { all, p, q, s, index ->
        val images = me.xdan.aperture.domain.catalog.JellyfinImages(index.baseUrl)
        val titles: List<GridTitle<GridEntry<MediaEntity>>> =
            all.map { m -> LocalLibraryQuery.movieTitle(m, p).withValue(GridEntry.Local(m, "local:${m.id}")) } +
                index.serverOnlyMovies.map { LocalLibraryQuery.remoteTitle(images.item(it), it.lastPlayedAt) }
        LocalLibraryQuery.arrange(titles, q, s, LocalLibraryQuery.movieSorts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalGrid())

    /** The same for shows: local show groups, plus server series the device has no episodes of. */
    val showGrid: StateFlow<LocalGrid<GridEntry<ShowGroup>>> = combine(showGroups, progress, showQuery, seed, hybrid.index) { all, p, q, s, index ->
        val images = me.xdan.aperture.domain.catalog.JellyfinImages(index.baseUrl)
        val titles: List<GridTitle<GridEntry<ShowGroup>>> =
            all.map { g -> LocalLibraryQuery.showTitle(g, p).withValue(GridEntry.Local(g, "show:${g.title}")) } +
                index.serverOnlyShows.map { LocalLibraryQuery.remoteTitle(images.item(it), it.lastPlayedAt) }
        LocalLibraryQuery.arrange(titles, q, s, LocalLibraryQuery.showSorts)
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
