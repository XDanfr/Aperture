package me.xdan.aperture.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MediaGoneTest {
    private val source = DesktopMediaLibrarySource()

    @Test
    fun `a deleted file is gone but one on an unplugged drive isn't`() {
        val folder = Files.createTempDirectory("aperture-gone").toFile()
        try {
            val kept = File(folder, "kept.mkv").apply { writeText("x") }
            assertFalse(source.isGone(kept.path))
            // Deleted from a folder that's still there.
            assertTrue(source.isGone(File(folder, "deleted.mkv").path))
            // The whole folder (or drive) is missing: can't tell, so keep it.
            assertFalse(source.isGone(File(folder, "unplugged/film.mkv").path))
        } finally {
            folder.deleteRecursively()
        }
    }
}
