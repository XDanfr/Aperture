package me.xdan.aperture.ui.screen.library

import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.domain.catalog.LibraryQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalLibraryQueryTest {
    private fun movie(id: Long, title: String, year: Int? = null, added: Long = id, duration: Long? = null, favourite: Boolean = false) =
        MediaEntity(id = id, filePath = "/m/$id.mkv", title = title, year = year, type = "MOVIE", duration = duration, isFavorite = favourite, dateAdded = added)

    private val films = listOf(
        movie(1, "The Matrix", 1999, added = 10, duration = 8_160_000),
        movie(2, "Alien", 1979, added = 30, duration = 7_020_000, favourite = true),
        movie(3, "Zodiac", 2007, added = 20),
        movie(4, "Untitled clip")
    )

    private fun titles(query: LibraryQuery, progress: Map<Long, PlaybackProgressEntity> = emptyMap()) =
        LocalLibraryQuery.movies(films, progress, query, seed = 1).items.map { it.title }

    @Test
    fun titleSortIgnoresLeadingArticle() {
        assertEquals(listOf("Alien", "The Matrix", "Untitled clip", "Zodiac"), titles(LibraryQuery()))
        assertEquals(listOf("Zodiac", "Untitled clip", "The Matrix", "Alien"), titles(LibraryQuery(descending = true)))
    }

    @Test
    fun missingValuesGoLastWhicheverDirection() {
        val newest = LibraryQuery(sort = LibraryQuery.Sort.RELEASED, descending = true)
        assertEquals(listOf("Zodiac", "The Matrix", "Alien", "Untitled clip"), titles(newest))
        val shortest = LibraryQuery(sort = LibraryQuery.Sort.RUNTIME)
        assertEquals(listOf("Alien", "The Matrix", "Untitled clip", "Zodiac"), titles(shortest))
    }

    @Test
    fun filtersByDecadeFavouritesAndWatched() {
        assertEquals(listOf("The Matrix"), titles(LibraryQuery(decade = 1990)))
        assertEquals(listOf("Alien"), titles(LibraryQuery(favouritesOnly = true)))
        val watched = mapOf(1L to PlaybackProgressEntity(mediaId = 1, position = 100, duration = 100, isCompleted = true))
        assertEquals(listOf("Alien", "Untitled clip", "Zodiac"), titles(LibraryQuery(unwatchedOnly = true), watched))
    }

    @Test
    fun offersDecadesAndNoRatingSort() {
        val grid = LocalLibraryQuery.movies(films, emptyMap(), LibraryQuery(), seed = 1)
        assertEquals(listOf(2000, 1990, 1970), grid.options.decades)
        assertTrue(LibraryQuery.Sort.RATING !in grid.options.sorts)
        assertEquals(setOf('A', 'M', 'U', 'Z'), grid.letters.keys)
    }

    @Test
    fun oldSavedQueriesStillDecode() {
        val old = listOf("DATE_ADDED", "true", "false", "true", "Drama").joinToString("\u001F")
        assertEquals(LibraryQuery(sort = LibraryQuery.Sort.DATE_ADDED, descending = true, favouritesOnly = true, genre = "Drama"), LibraryQuery.decode(old))
        val current = LibraryQuery(decade = 1980, uhdOnly = true)
        assertEquals(current, LibraryQuery.decode(current.encode()))
    }

    @Test
    fun localAndServerTitlesSortAndFilterTogether() {
        fun server(n: Int, title: String, year: Int, played: Boolean = false) = me.xdan.aperture.domain.catalog.CatalogItem(
            key = me.xdan.aperture.domain.model.MediaKey.Jellyfin(1, "s$n"),
            kind = me.xdan.aperture.domain.catalog.CatalogItem.Kind.MOVIE,
            title = title, year = year, played = played, dateAdded = 100L + n
        )
        val titles: List<GridTitle<GridEntry<MediaEntity>>> =
            films.take(2).map { LocalLibraryQuery.movieTitle(it, emptyMap()).withValue(GridEntry.Local(it, "local:${it.id}")) } +
                listOf(LocalLibraryQuery.remoteTitle(server(1, "Blade Runner", 1982), null), LocalLibraryQuery.remoteTitle(server(2, "Heat", 1995, played = true), null))

        fun names(query: LibraryQuery) = LocalLibraryQuery.arrange(titles, query, 1, LocalLibraryQuery.movieSorts).items.map {
            when (it) {
                is GridEntry.Local -> it.value.title
                is GridEntry.Remote -> it.item.title
            }
        }
        assertEquals(listOf("Alien", "Blade Runner", "Heat", "The Matrix"), names(LibraryQuery()))
        assertEquals(listOf("Alien", "Blade Runner", "The Matrix"), names(LibraryQuery(unwatchedOnly = true)))
        assertEquals(listOf("Heat", "The Matrix"), names(LibraryQuery(decade = 1990)))
        // Newest first: server dates (101, 102) sit between the local ones (10, 30).
        assertEquals(listOf("Heat", "Blade Runner", "Alien", "The Matrix"), names(LibraryQuery(sort = LibraryQuery.Sort.DATE_ADDED, descending = true)))
    }
}
