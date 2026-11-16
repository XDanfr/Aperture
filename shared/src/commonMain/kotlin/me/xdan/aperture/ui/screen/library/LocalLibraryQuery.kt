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

/**
 * Sorting and filtering for local files. Local titles have no ratings or genres, so those aren't
 * offered; shows sort by their newest episode and count as unwatched while any episode is.
 */
internal object LocalLibraryQuery {
    val movieSorts = listOf(
        LibraryQuery.Sort.TITLE, LibraryQuery.Sort.DATE_ADDED, LibraryQuery.Sort.RELEASED,
        LibraryQuery.Sort.RUNTIME, LibraryQuery.Sort.LAST_PLAYED, LibraryQuery.Sort.RANDOM
    )
    val showSorts = movieSorts - LibraryQuery.Sort.RUNTIME

    fun watched(progress: PlaybackProgressEntity?): Boolean = progress != null &&
        (progress.isCompleted || (progress.duration > 0 && progress.position >= progress.duration * 0.95))

    fun movies(all: List<MediaEntity>, progress: Map<Long, PlaybackProgressEntity>, query: LibraryQuery, seed: Int): LocalGrid<MediaEntity> {
        val filtered = all.filter { media ->
            (!query.unwatchedOnly || !watched(progress[media.id])) &&
                (!query.favouritesOnly || media.isFavorite) &&
                (query.decade == null || media.year?.let { it in query.decade..query.decade + 9 } == true)
        }
        val items = ordered(filtered, query, seed, title = { it.title }) { media ->
            when (query.sort) {
                LibraryQuery.Sort.DATE_ADDED -> media.dateAdded
                LibraryQuery.Sort.RELEASED -> media.year?.toLong()
                LibraryQuery.Sort.RUNTIME -> media.duration?.takeIf { it > 0 }
                LibraryQuery.Sort.LAST_PLAYED -> progress[media.id]?.lastUpdated
                else -> null
            }
        }
        return LocalGrid(
            items = items,
            total = all.size,
            query = query,
            letters = letters(items.map { it.title }, query),
            options = LibraryOptions(sorts = movieSorts, decades = decades(all.mapNotNull { it.year }))
        )
    }

    fun shows(all: List<ShowGroup>, progress: Map<Long, PlaybackProgressEntity>, query: LibraryQuery, seed: Int): LocalGrid<ShowGroup> {
        val filtered = all.filter { group ->
            val year = group.episodes.mapNotNull { it.year }.minOrNull()
            (!query.unwatchedOnly || group.episodes.any { !watched(progress[it.id]) }) &&
                (!query.favouritesOnly || group.episodes.any { it.isFavorite }) &&
                (query.decade == null || year?.let { it in query.decade..query.decade + 9 } == true)
        }
        val items = ordered(filtered, query, seed, title = { it.title }) { group ->
            when (query.sort) {
                LibraryQuery.Sort.DATE_ADDED -> group.episodes.maxOf { it.dateAdded }
                LibraryQuery.Sort.RELEASED -> group.episodes.mapNotNull { it.year }.minOrNull()?.toLong()
                LibraryQuery.Sort.LAST_PLAYED -> group.episodes.mapNotNull { progress[it.id]?.lastUpdated }.maxOrNull()
                else -> null
            }
        }
        return LocalGrid(
            items = items,
            total = all.size,
            query = query,
            letters = letters(items.map { it.title }, query),
            options = LibraryOptions(sorts = showSorts, decades = decades(all.flatMap { g -> g.episodes.mapNotNull { it.year } }))
        )
    }

    /** Titles without the sort value (no year, never played) go last either way, then by title. */
    private fun <T> ordered(items: List<T>, query: LibraryQuery, seed: Int, title: (T) -> String, key: (T) -> Long?): List<T> {
        val byTitle = compareBy<T> { sortableTitle(title(it)) }
        return when (query.sort) {
            LibraryQuery.Sort.RANDOM -> items.sortedWith(byTitle).shuffled(Random(seed))
            LibraryQuery.Sort.TITLE, LibraryQuery.Sort.RATING -> items.sortedWith(if (query.descending) byTitle.reversed() else byTitle)
            else -> {
                val (known, unknown) = items.partition { key(it) != null }
                val byKey = compareBy<T> { key(it) }
                known.sortedWith((if (query.descending) byKey.reversed() else byKey).then(byTitle)) + unknown.sortedWith(byTitle)
            }
        }
    }

    private fun letters(titles: List<String>, query: LibraryQuery): Map<Char, Int> =
        if (query.sort == LibraryQuery.Sort.TITLE) letterIndex(titles, query.descending) else emptyMap()

    private fun decades(years: List<Int>): List<Int> = years.map { it / 10 * 10 }.distinct().sortedDescending()
}
