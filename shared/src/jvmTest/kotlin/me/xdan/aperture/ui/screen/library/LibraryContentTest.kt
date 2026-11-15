package me.xdan.aperture.ui.screen.library

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.xdan.aperture.data.local.entity.MediaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryContentTest {
    @Test
    fun reopeningALargeLibraryReusesOneLiveSubscriptionAndTheLatestSnapshot() = runTest {
        val original = (1L..1_200L).map { movie(it) }
        val source = MutableStateFlow(original)
        var subscriptions = 0
        val content = LibraryContent(
            movies = flow { subscriptions++; emitAll(source) },
            episodes = MutableStateFlow(emptyList()),
            scope = backgroundScope,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        assertEquals(0, subscriptions)
        val firstVisit = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            content.movies.collect {}
        }
        runCurrent()
        assertSame(original, content.movies.value)
        firstVisit.cancel()
        advanceTimeBy(10_000)

        // A hidden title disappears and refreshed artwork arrives while the page is closed.
        val updated = original.drop(1).map { if (it.id == 2L) it.copy(posterPath = "/new") else it }
        source.value = updated
        runCurrent()
        assertSame(updated, content.movies.value)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { content.movies.collect {} }
        runCurrent()
        assertEquals(1, subscriptions)
        assertEquals(1_199, content.movies.value.size)
        assertEquals("/new", content.movies.value.first().posterPath)
    }

    @Test
    fun moviesDoNotStartTheEpisodeQueryAndBothQueriesStopWithTheirOwner() = runTest {
        var moviesActive = 0
        var episodesActive = 0
        val scopeJob = kotlinx.coroutines.Job(backgroundScope.coroutineContext[kotlinx.coroutines.Job])
        val scope = kotlinx.coroutines.CoroutineScope(backgroundScope.coroutineContext + scopeJob)
        val content = LibraryContent(
            movies = flow {
                moviesActive++
                try { emitAll(MutableStateFlow(listOf(movie(1)))) } finally { moviesActive-- }
            },
            episodes = flow {
                episodesActive++
                try { emitAll(MutableStateFlow(emptyList<MediaEntity>())) } finally { episodesActive-- }
            },
            scope = scope,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { content.movies.collect {} }
        runCurrent()
        assertEquals(1, moviesActive)
        assertEquals(0, episodesActive)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { content.showGroups.collect {} }
        runCurrent()
        assertEquals(1, episodesActive)
        scopeJob.cancel()
        runCurrent()
        assertEquals(0, moviesActive)
        assertEquals(0, episodesActive)
    }

    @Test
    fun tenThousandEpisodesArePreparedInTitleSeasonAndEpisodeOrder() = runTest {
        val episodes = (0 until 100).flatMap { show ->
            (1..10).flatMap { season ->
                (1..10).map { episode ->
                    MediaEntity(
                        id = (show * 100 + (season - 1) * 10 + episode).toLong(),
                        filePath = "$show/$season/$episode",
                        title = "Show ${show.toString().padStart(3, '0')}",
                        type = "EPISODE",
                        seasonNumber = season,
                        episodeNumber = episode
                    )
                }
            }
        }.reversed()
        val source = MutableStateFlow(episodes)
        val content = LibraryContent(
            MutableStateFlow(emptyList()), source, backgroundScope,
            StandardTestDispatcher(testScheduler)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { content.showGroups.collect {} }
        runCurrent()
        val groups = content.showGroups.value
        assertEquals(100, groups.size)
        assertEquals("Show 000", groups.first().title)
        assertEquals("Show 099", groups.last().title)
        groups.forEach { group ->
            assertEquals((1..10).toList(), group.seasons.map { it.number })
            group.seasons.forEach { season ->
                assertEquals((1..10).toList(), season.episodes.map { it.episodeNumber })
            }
            assertEquals(1, group.representative.seasonNumber)
            assertEquals(1, group.representative.episodeNumber)
        }

        source.value = episodes.filterNot { it.title == "Show 000" }
        runCurrent()
        assertEquals(99, content.showGroups.value.size)
        assertEquals("Show 001", content.showGroups.value.first().title)
    }

    @Test
    fun unknownEpisodeNumbersAndArtworkChangesPreserveShowGrouping() = runTest {
        val first = movie(1).copy(title = "Show", type = "EPISODE", seasonNumber = 1, episodeNumber = 1)
        val unknown = first.copy(id = 2, seasonNumber = null, episodeNumber = null)
        val source = MutableStateFlow(listOf(unknown, first))
        val content = LibraryContent(
            MutableStateFlow(emptyList()), source, backgroundScope,
            StandardTestDispatcher(testScheduler)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { content.showGroups.collect {} }
        runCurrent()
        val group = content.showGroups.value.single()
        assertEquals(first, group.representative)
        assertEquals(listOf(0, 1), group.seasons.map { it.number })
        source.value = listOf(unknown, first.copy(posterPath = "/updated", isFavorite = true))
        runCurrent()
        assertEquals("/updated", content.showGroups.value.single().representative.posterPath)
        assertEquals(true, content.showGroups.value.single().representative.isFavorite)
    }

    private fun movie(id: Long) = MediaEntity(
        id = id, filePath = "$id", title = "Movie ${id.toString().padStart(4, '0')}", type = "MOVIE"
    )
}
