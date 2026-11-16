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
}
