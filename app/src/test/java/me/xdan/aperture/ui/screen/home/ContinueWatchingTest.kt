package me.xdan.aperture.ui.screen.home

import me.xdan.aperture.domain.playback.nextEpisodeForContinueWatching
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinueWatchingTest {
    @Test
    fun `most recently watched partial episode wins`() {
        val episodes = listOf(episode(1, 1, 1), episode(2, 1, 2))
        val progress = mapOf(
            1L to partial(1, updated = 100),
            2L to partial(2, updated = 200)
        )
        assertEquals(2L, nextEpisodeForContinueWatching(episodes, progress)?.first?.id)
    }

    @Test
    fun `restarted partial episode remains in continue watching`() {
        val episodes = listOf(episode(1, 1, 1))
        val progress = mapOf(
            1L to partial(1, position = 0, updated = 200, keep = true)
        )
        assertEquals(1L, nextEpisodeForContinueWatching(episodes, progress)?.first?.id)
    }

    @Test
    fun `completion advances into next season`() {
        val episodes = listOf(episode(1, 1, 8), episode(2, 2, 1))
        val progress = mapOf(1L to completed(1, updated = 200))
        assertEquals(2L, nextEpisodeForContinueWatching(episodes, progress)?.first?.id)
    }

    @Test
    fun `finished show leaves continue watching`() {
        val episodes = listOf(episode(1, 1, 1))
        assertNull(nextEpisodeForContinueWatching(episodes, mapOf(1L to completed(1, 200))))
    }

    @Test
    fun `partial progress from older versions is resumed without the sticky flag`() {
        val episodes = listOf(episode(1, 1, 1), episode(2, 1, 2))
        val progress = mapOf(2L to partial(2, updated = 200, keep = false))
        assertEquals(2L, nextEpisodeForContinueWatching(episodes, progress)?.first?.id)
    }

    @Test
    fun `recently restarted earlier episode takes priority over later completions`() {
        val episodes = listOf(episode(1, 1, 1), episode(2, 1, 2), episode(3, 1, 3))
        val progress = mapOf(
            1L to partial(1, position = 0, updated = 300, keep = true),
            2L to completed(2, updated = 200)
        )
        assertEquals(1L, nextEpisodeForContinueWatching(episodes, progress)?.first?.id)
    }

    @Test
    fun `rewatched completion continues from that episode instead of later history`() {
        val episodes = listOf(episode(1, 1, 1), episode(2, 1, 2), episode(3, 1, 3))
        val progress = mapOf(
            1L to completed(1, updated = 300),
            2L to completed(2, updated = 200)
        )
        assertEquals(2L, nextEpisodeForContinueWatching(episodes, progress)?.first?.id)
    }

    private fun episode(id: Long, season: Int, episode: Int) = MediaEntity(
        id = id,
        filePath = "/show/S${season}E$episode.mkv",
        title = "Example Show",
        type = "EPISODE",
        seasonNumber = season,
        episodeNumber = episode
    )

    private fun partial(
        id: Long,
        position: Long = 50,
        updated: Long,
        keep: Boolean = true
    ) = PlaybackProgressEntity(
        mediaId = id,
        position = position,
        duration = 100,
        lastUpdated = updated,
        keepInContinueWatching = keep
    )

    private fun completed(id: Long, updated: Long) = PlaybackProgressEntity(
        mediaId = id,
        position = 100,
        duration = 100,
        lastUpdated = updated,
        isCompleted = true,
        completedAt = updated
    )
}
