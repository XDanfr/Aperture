package me.xdan.aperture.domain.hybrid

import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.RemoteItemEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HybridMatcherTest {
    private val libraries = mapOf("films" to "movies", "tv" to "tvshows", "clips" to "homevideos", "discover" to null)

    private fun localMovie(id: Long, tmdb: Int?) = MediaEntity(id = id, filePath = "/m/$id.mkv", title = "Film $id", type = "MOVIE", tmdbId = tmdb)
    private fun localEpisode(id: Long, tmdb: Int?, season: Int, number: Int, title: String = "Show") =
        MediaEntity(id = id, filePath = "/s/$id.mkv", title = title, type = "EPISODE", tmdbId = tmdb, seasonNumber = season, episodeNumber = number)
    private fun remote(id: String, type: String, library: String, tmdb: String? = null, series: String? = null, season: Int? = null, number: Int? = null) =
        RemoteItemEntity(accountId = 1, itemId = id, type = type, name = id, libraryId = library, tmdbId = tmdb, seriesId = series, seasonNumber = season, episodeNumber = number)

    @Test
    fun filmsMatchByTmdbIdAndVersionsGroupTogether() {
        val index = HybridMatcher.match(
            local = listOf(localMovie(1, 603), localMovie(2, 999), localMovie(3, null)),
            remote = listOf(remote("a", "MOVIE", "films", "603"), remote("b", "MOVIE", "films", "603"), remote("c", "MOVIE", "films", "11")),
            libraryTypes = libraries
        )
        val matrix = index.movies.single { it.tmdbId == "603" }
        assertEquals(listOf(1L), matrix.local.map { it.id })
        assertEquals(listOf("a", "b"), matrix.remote.map { it.itemId })
        assertEquals(setOf("a", "b"), index.matchedRemoteIds)
        assertEquals(listOf("a", "b"), index.remoteFor[1L]?.map { it.itemId })
        // Unmatched ones stay on their own.
        assertTrue(index.movies.any { it.tmdbId == "999" && it.remote.isEmpty() })
        assertTrue(index.movies.any { it.tmdbId == "11" && it.local.isEmpty() })
        assertTrue(index.movies.any { it.tmdbId == null && it.local.single().id == 3L })
    }

    @Test
    fun clipsAndDiscoverPlaceholdersNeverMatch() {
        val index = HybridMatcher.match(
            local = listOf(localMovie(1, 603)),
            remote = listOf(remote("clip", "MOVIE", "clips", "603"), remote("placeholder", "MOVIE", "discover", "603")),
            libraryTypes = libraries
        )
        assertTrue(index.matchedRemoteIds.isEmpty())
        assertEquals(emptyList(), index.movies.single().remote)
    }

    @Test
    fun episodesMatchBySeasonAndNumberWithinTheShow() {
        val index = HybridMatcher.match(
            local = listOf(localEpisode(1, 1399, 1, 1), localEpisode(2, 1399, 1, 2), localEpisode(3, 1399, 2, 5)),
            remote = listOf(
                remote("got", "SERIES", "tv", "1399"),
                remote("e11", "EPISODE", "tv", series = "got", season = 1, number = 1),
                remote("e12", "EPISODE", "tv", series = "got", season = 1, number = 2)
            ),
            libraryTypes = libraries
        )
        val show = index.shows.single()
        assertEquals("1399", show.tmdbId)
        assertEquals(mapOf(1L to listOf("e11"), 2L to listOf("e12")), show.episodeMatches.mapValues { (_, v) -> v.map { it.itemId } })
        assertEquals(setOf("got"), index.matchedRemoteIds)
    }

    @Test
    fun localShowsWithoutAnIdGroupByTitle() {
        val index = HybridMatcher.match(
            local = listOf(localEpisode(1, null, 1, 1, "Home Show"), localEpisode(2, null, 1, 2, "home show")),
            remote = emptyList(),
            libraryTypes = libraries
        )
        val show = index.shows.single()
        assertEquals(null, show.tmdbId)
        assertEquals(2, show.localEpisodes.size)
    }

    @Test
    fun onlyEpisodesInBothPlacesAreDuplicates() {
        // Season 1 is on this device; season 2 only on the server.
        val index = HybridMatcher.match(
            local = listOf(localEpisode(1, 1399, 1, 1, title = "Game of Thrones (2011)")),
            remote = listOf(
                remote("got", "SERIES", "tv", "1399"),
                remote("e11", "EPISODE", "tv", series = "got", season = 1, number = 1),
                remote("e21", "EPISODE", "tv", series = "got", season = 2, number = 1),
                remote("m", "MOVIE", "films", "603")
            ),
            libraryTypes = libraries
        )
        assertEquals(setOf("e11"), index.duplicateRemoteItemIds)
        // The server's other episodes list under the local show's title.
        assertEquals(mapOf("got" to "Game of Thrones (2011)"), index.localTitleForSeries)
    }
}
