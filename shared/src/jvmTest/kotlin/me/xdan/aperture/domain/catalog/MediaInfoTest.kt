package me.xdan.aperture.domain.catalog

import me.xdan.aperture.domain.model.MediaKey
import me.xdan.aperture.ui.screen.jellyfin.JellyfinDetailsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaInfoTest {
    @Test
    fun formatsSizesBitratesAndDurations() {
        assertEquals("512 B", MediaInfo.formatSize(512))
        assertEquals("4.0 GB", MediaInfo.formatSize(4L * 1024 * 1024 * 1024))
        assertEquals("13.3 Mbps", MediaInfo.formatBitrate(13_300_000))
        assertEquals("157 kbps", MediaInfo.formatBitrate(157_000))
        assertEquals("42:46", MediaInfo.formatDuration((42 * 60 + 46) * 1000L))
        assertEquals("2:01:05", MediaInfo.formatDuration((2 * 3600 + 65) * 1000L))
    }

    @Test
    fun labelsResolutionsByWidthForWidescreen() {
        assertEquals("4K", MediaInfo.resolutionLabel(3840, 1600))
        assertEquals("1080p", MediaInfo.resolutionLabel(1920, 800))
        assertEquals("720p", MediaInfo.resolutionLabel(1280, 720))
        assertEquals("480p", MediaInfo.resolutionLabel(720, 480))
    }

    @Test
    fun defaultEpisodePrefersInProgressThenNextUnwatched() {
        fun ep(n: Int, played: Boolean = false, progress: Float = 0f) = CatalogItem(
            key = MediaKey.Jellyfin(1, "e$n"), kind = CatalogItem.Kind.EPISODE, title = "Show",
            seasonNumber = 1, episodeNumber = n, played = played, progress = progress
        )
        assertEquals("e1", id(JellyfinDetailsViewModel.defaultEpisode(listOf(ep(1), ep(2)))))
        assertEquals("e3", id(JellyfinDetailsViewModel.defaultEpisode(listOf(ep(1, played = true), ep(2, played = true), ep(3)))))
        assertEquals("e2", id(JellyfinDetailsViewModel.defaultEpisode(listOf(ep(1, played = true), ep(2, progress = 0.4f), ep(3)))))
        // Everything watched: start again from the first.
        assertEquals("e1", id(JellyfinDetailsViewModel.defaultEpisode(listOf(ep(1, played = true), ep(2, played = true)))))
    }

    private fun id(item: CatalogItem?) = (item?.key as MediaKey.Jellyfin).itemId
}
