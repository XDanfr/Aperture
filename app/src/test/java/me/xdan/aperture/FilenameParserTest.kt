package me.xdan.aperture

import me.xdan.aperture.util.FilenameParser
import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameParserTest {
    @Test
    fun parsesCompactSeasonEpisodeFormat() {
        val result = FilenameParser.parse("The.Four.Seasons.S02E04.Spring.mkv")

        assertEquals("The Four Seasons", result.title)
        assertEquals(2, result.season)
        assertEquals(4, result.episode)
    }

    @Test
    fun parsesSpacedSeasonEpisodeFormatUsingFolders() {
        val result = FilenameParser.parse(
            filename = "S2 E4 Spring.mkv",
            filePath = "/media/TV/The Four Seasons/Season 2/S2 E4 Spring.mkv"
        )

        assertEquals("The Four Seasons", result.title)
        assertEquals(2, result.season)
        assertEquals(4, result.episode)
    }

    @Test
    fun stripsSeasonSuffixFromShowFolderNames() {
        val result = FilenameParser.parse(
            filename = "S2 E8 Maratona.mp4",
            filePath = "/media/Tv Shows/The Four Seasons Season 2/S2 E8 Maratona.mp4"
        )

        assertEquals("The Four Seasons", result.title)
        assertEquals(2, result.season)
        assertEquals(8, result.episode)
    }

    @Test
    fun parsesCrossSeasonEpisodeFormat() {
        val result = FilenameParser.parse("The Four Seasons 2x04 Spring.mkv")

        assertEquals("The Four Seasons", result.title)
        assertEquals(2, result.season)
        assertEquals(4, result.episode)
    }

    @Test
    fun preservesMovieSequelNumber() {
        val result = FilenameParser.parse("Five Nights at Freddy's 2 2025.mkv")

        assertEquals("Five Nights at Freddy's 2", result.title)
        assertEquals(2025, result.year)
    }
}
