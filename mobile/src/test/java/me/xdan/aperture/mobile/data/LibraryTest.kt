package me.xdan.aperture.mobile.data

import me.xdan.aperture.companion.protocol.CollectionKind
import me.xdan.aperture.companion.protocol.CompanionCollection
import me.xdan.aperture.companion.protocol.CompanionMedia
import me.xdan.aperture.companion.protocol.CompanionMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val collections = listOf(
        CompanionCollection("library:films", "Films", CollectionKind.MOVIES, "movie"),
        CompanionCollection("library:tv", "Shows", CollectionKind.SHOWS, "tv"),
        CompanionCollection("library:home", "Home videos", CollectionKind.HOME_VIDEOS, "videocam"),
        CompanionCollection("library:discover", "Discover", CollectionKind.DISCOVER, "explore")
    )

    private fun media(id: Long, title: String, collection: String, type: CompanionMediaType = CompanionMediaType.MOVIE, favourite: Boolean = false, added: Long = id) =
        CompanionMedia(id = id, type = type, title = title, collection = collection, isFavorite = favourite, dateAdded = added)

    private val library = Library(
        listOf(
            media(-1, "Heat", "library:films", favourite = true),
            media(-2, "Birthday 2019", "library:home"),
            media(-3, "Show", "library:tv", CompanionMediaType.EPISODE),
            media(-4, "Show", "library:tv", CompanionMediaType.EPISODE),
            media(-5, "Dune Part Three", "library:discover", favourite = true, added = 100)
        ),
        collections
    )

    @Test
    fun `each collection lists its own titles, episodes grouped into shows`() {
        assertEquals(listOf("Heat"), library.entriesIn("library:films").map { it.title })
        assertEquals(listOf("Birthday 2019"), library.entriesIn("library:home").map { it.title })
        assertEquals(1, library.count("library:tv"))
    }

    @Test
    fun `Discover titles stay out of Home and My List`() {
        assertTrue(library.hasDiscover)
        assertEquals(listOf("Dune Part Three"), library.discover.map { it.title })
        assertTrue(library.recentlyAdded.none { it.title == "Dune Part Three" })
        // A requested title is a server favourite, but not something in My List.
        assertEquals(listOf("Heat"), library.favourites.map { it.title })
    }

    @Test
    fun `an older TV without collections gets Movies and TV shows`() {
        val old = Library(listOf(CompanionMedia(id = 1, type = CompanionMediaType.MOVIE, title = "Alien")))
        assertEquals(listOf(FALLBACK_MOVIES, FALLBACK_SHOWS), old.collections.map { it.key })
        assertEquals(listOf("Alien"), old.entriesIn(FALLBACK_MOVIES).map { it.title })
        assertFalse(old.hasDiscover)
    }
}
