package me.xdan.aperture.data.jellyfin

import kotlin.test.Test
import kotlin.test.assertEquals

class IntroSkipperSegmentsTest {
    @Test
    fun parsesNewerAndOlderFields() {
        val body = """{"Introduction":{"EpisodeId":"x","Valid":true,"Start":12.5,"End":72.0},
            "Credits":{"Valid":true,"IntroStart":1300,"IntroEnd":1380}}"""
        assertEquals(
            listOf(
                JellyfinPlayback.Segment(JellyfinPlayback.SegmentKind.INTRO, 12_500, 72_000),
                JellyfinPlayback.Segment(JellyfinPlayback.SegmentKind.OUTRO, 1_300_000, 1_380_000)
            ),
            parseIntroSkipperSegments(body)
        )
    }

    @Test
    fun dropsInvalidAndEmpty() {
        val body = """{"Introduction":{"Valid":false,"Start":0,"End":0},"Credits":{"Start":5,"End":5}}"""
        assertEquals(emptyList(), parseIntroSkipperSegments(body))
    }
}
