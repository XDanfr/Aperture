package me.xdan.aperture.ui.screen.player

import me.xdan.aperture.data.jellyfin.JellyfinPlayback.Segment
import me.xdan.aperture.data.jellyfin.JellyfinPlayback.SegmentKind
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerChaptersTest {
    private val chapters = buildChapters(
        listOf(Segment(SegmentKind.INTRO, 70_000, 160_000), Segment(SegmentKind.RECAP, 0, 45_000), Segment(SegmentKind.OUTRO, 1_230_000, 1_320_000)),
        1_320_000
    )

    @Test
    fun fillsTheGapsWithTheEpisode() {
        assertEquals(listOf("Recap", "Episode", "Intro", "Episode", "Credits"), chapters.map { it.name })
        assertEquals(1_320_000, chapters.last().endMs)
    }

    @Test
    fun jumpsLikeYouTube() {
        assertEquals(70_000, chapters.jump(50_000, 1))
        assertEquals(160_000, chapters.jump(100_000, 1))
        assertEquals(70_000, chapters.jump(100_000, -1)) // back to this chapter's start
        assertEquals(45_000, chapters.jump(71_000, -1)) // near the start: the previous chapter
    }

    @Test
    fun noSegmentsMeansNoChapters() {
        assertEquals(emptyList(), buildChapters(emptyList(), 1_000_000))
    }
}
