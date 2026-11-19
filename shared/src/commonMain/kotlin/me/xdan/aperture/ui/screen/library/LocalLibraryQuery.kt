package me.xdan.aperture.ui.screen.library

import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.domain.catalog.LibraryQuery
import me.xdan.aperture.domain.catalog.letterIndex
import me.xdan.aperture.domain.catalog.sortableTitle
import me.xdan.aperture.ui.component.LibraryOptions
import kotlin.random.Random

/** A local grid after its query: what to show, and what the controls can offer. */
data class LocalGrid<T>(
    val items: List<T> = emptyList(),
    /** Before filtering, so an empty filter result isn't mistaken for an empty library. */
    val total: Int = 0,
    val query: LibraryQuery = LibraryQuery(),
    val letters: Map<Char, Int> = emptyMap(),
    val options: LibraryOptions = LibraryOptions()
)

/** A title in a combined grid: a local one, or one only the server has. */
sealed interface GridEntry<out L> {
    val key: String

    data class Local<L>(val value: L, override val key: String) : GridEntry<L>
    data class Remote(val item: me.xdan.aperture.domain.catalog.CatalogItem) : GridEntry<Nothing> {
        override val key: String get() = item.key.stableId
    }
}

/** What sorting and filtering needs from any title, local or remote. */
internal data class GridTitle<out T>(
    val value: T,
    val title: String,
    val year: Int?,
    val added: Long?,
    val runtime: Long?,
    val lastPlayed: Long?,
    val watched: Boolean,
    val favourite: Boolean,
    val genres: List<String> = emptyList(),
    val rating: Float? = null,
    val uhd: Boolean = false
) {
    fun <R> withValue(value: R): GridTitle<R> = GridTitle(value, title, year, added, runtime, lastPlayed, watched, favourite, genres, rating, uhd)
}

/**
 * Sorting and filtering for local grids, and for hybrid grids that add the server's titles: the
 * same sorts and filters as a Jellyfin library. Genres and ratings come from TMDB, 4K from the
 * file name; shows sort by their newest episode and count as unwatched while any episode is.
 */
internal object LocalLibraryQuery {
    val movieSorts = listOf(
        LibraryQuery.Sort.TITLE, LibraryQuery.Sort.DATE_ADDED, LibraryQuery.Sort.RELEASED,
        LibraryQuery.Sort.RATING, LibraryQuery.Sort.RUNTIME, LibraryQuery.Sort.LAST_PLAYED, LibraryQuery.Sort.RANDOM
    )
    val showSorts = movieSorts - LibraryQuery.Sort.RUNTIME

    fun watched(progress: PlaybackProgressEntity?): Boolean = progress != null &&
        (progress.isCompleted || (progress.duration > 0 && progress.position >= progress.duration * 0.95))

    fun movieTitle(media: MediaEntity, progress: Map<Long, PlaybackProgressEntity>) = GridTitle(
        media, media.title, media.year, media.dateAdded, media.duration?.takeIf { it > 0 },
        progress[media.id]?.lastUpdated, watched(progress[media.id]), media.isFavorite,
        genresOf(media.genres), media.rating, (media.videoHeight ?: 0) >= 2000
    )

    private fun genresOf(value: String?): List<String> = value?.split('|')?.filter { it.isNotBlank() }.orEmpty()

    fun showTitle(group: ShowGroup, progress: Map<Long, PlaybackProgressEntity>) = GridTitle(
        group, group.title,
        group.episodes.mapNotNull { it.year }.minOrNull(),
        group.episodes.maxOf { it.dateAdded },
        null,
        group.episodes.mapNotNull { progress[it.id]?.lastUpdated }.maxOrNull(),
        group.episodes.none { !watched(progress[it.id]) },
        group.episodes.any { it.isFavorite },
        group.episodes.firstNotNullOfOrNull { it.genres }.let(::genresOf),
        group.episodes.firstNotNullOfOrNull { it.rating }
    )

    fun remoteTitle(item: me.xdan.aperture.domain.catalog.CatalogItem, lastPlayed: Long?) = GridTitle<GridEntry<Nothing>>(
        GridEntry.Remote(item), item.title, item.year, item.dateAdded, item.runtimeMs?.takeIf { it > 0 },
        lastPlayed, item.played, item.isFavorite, item.genres, item.rating
    )

    fun movies(all: List<MediaEntity>, progress: Map<Long, PlaybackProgressEntity>, query: LibraryQuery, seed: Int): LocalGrid<MediaEntity> =
        arrange(all.map { movieTitle(it, progress) }, query, seed, movieSorts)

    fun shows(all: List<ShowGroup>, progress: Map<Long, PlaybackProgressEntity>, query: LibraryQuery, seed: Int): LocalGrid<ShowGroup> =
        arrange(all.map { showTitle(it, progress) }, query, seed, showSorts)

    /** Filters, sorts and indexes any titles, and works out what the controls can offer. */
    fun <T> arrange(all: List<GridTitle<T>>, query: LibraryQuery, seed: Int, sorts: List<LibraryQuery.Sort>): LocalGrid<T> {
        val filtered = all.filter { t ->
            (!query.unwatchedOnly || !t.watched) &&
                (!query.favouritesOnly || t.favourite) &&
                (query.decade == null || t.year?.let { it in query.decade..query.decade + 9 } == true) &&
                (query.genre == null || query.genre in t.genres) &&
                (!query.uhdOnly || t.uhd)
        }
        val items = ordered(filtered, query, seed) { t ->
            when (query.sort) {
                LibraryQuery.Sort.DATE_ADDED -> t.added
                LibraryQuery.Sort.RELEASED -> t.year?.toLong()
                LibraryQuery.Sort.RUNTIME -> t.runtime
                LibraryQuery.Sort.LAST_PLAYED -> t.lastPlayed
                LibraryQuery.Sort.RATING -> t.rating?.let { (it * 100).toLong() }
                else -> null
            }
        }
        return LocalGrid(
            items = items.map { it.value },
            total = all.size,
            query = query,
            letters = if (query.sort == LibraryQuery.Sort.TITLE) letterIndex(items.map { it.title }, query.descending) else emptyMap(),
            options = LibraryOptions(
                sorts = sorts,
                genres = all.flatMap { it.genres }.distinct().sortedBy { it.lowercase() },
                decades = all.mapNotNull { it.year }.map { it / 10 * 10 }.distinct().sortedDescending(),
                offerUhd = all.any { it.uhd }
            )
        )
    }

    /** Titles without the sort value (no year, never played) go last either way, then by title. */
    private fun <T> ordered(items: List<GridTitle<T>>, query: LibraryQuery, seed: Int, key: (GridTitle<T>) -> Long?): List<GridTitle<T>> {
        val byTitle = compareBy<GridTitle<T>> { sortableTitle(it.title) }
        return when (query.sort) {
            LibraryQuery.Sort.RANDOM -> items.sortedWith(byTitle).shuffled(Random(seed))
            LibraryQuery.Sort.TITLE -> items.sortedWith(if (query.descending) byTitle.reversed() else byTitle)
            else -> {
                val (known, unknown) = items.partition { key(it) != null }
                val byKey = compareBy<GridTitle<T>> { key(it) }
                known.sortedWith((if (query.descending) byKey.reversed() else byKey).then(byTitle)) + unknown.sortedWith(byTitle)
            }
        }
    }
}
