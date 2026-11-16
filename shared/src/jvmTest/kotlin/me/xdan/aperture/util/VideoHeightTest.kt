package me.xdan.aperture.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoHeightTest {
    @Test
    fun `reads the resolution a release name states`() {
        assertEquals(2160, FilenameParser.videoHeight("Film.2001.2160p.UHD.Blu-ray.REMUX.mkv"))
        assertEquals(2160, FilenameParser.videoHeight("Film (2001) 4K.mkv"))
        assertEquals(1080, FilenameParser.videoHeight("Show.S01E02.1080p.WEB-DL.mkv"))
        assertEquals(720, FilenameParser.videoHeight("Show.S01E02.720p.mkv"))
        assertNull(FilenameParser.videoHeight("Home video.mp4"))
        // Not a tag on its own.
        assertNull(FilenameParser.videoHeight("Room 4KB.mkv"))
    }
}
