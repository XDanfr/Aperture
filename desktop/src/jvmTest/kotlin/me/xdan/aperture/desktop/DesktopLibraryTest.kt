package me.xdan.aperture.desktop

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class DesktopLibraryTest {
    @Test
    fun settingsRoundTripPreservesUnicodeAndPathSeparators() {
        val directory = Files.createTempDirectory("aperture-settings-test")
        try {
            val store = DesktopLibraryStore(directory.resolve("config/library.properties"))
            assertEquals(LibrarySettings(), store.load())
            val settings = LibrarySettings(listOf("C:\\Videos\\été", "/media/Movies: family"), setOf("/media/影片/Film.mkv"))
            store.save(settings)
            assertEquals(settings, store.load())
            store.save(settings.copy(folders = emptyList()))
            assertEquals(emptyList(), store.load().folders)
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test
    fun scannerDeduplicatesOverlappingRootsAndReportsMissingFolders() = runBlocking {
        val directory = Files.createTempDirectory("aperture-scan-test")
        try {
            val shows = Files.createDirectories(directory.resolve("Shows/Example/Season 2"))
            Files.createFile(shows.resolve("S02E03.MKV"))
            Files.createFile(directory.resolve("Movie.2025.mp4"))
            Files.createFile(directory.resolve("ignore.txt"))
            val result = scanFolders(listOf(directory.toString(), shows.toString(), directory.resolve("missing").toString()))
            assertEquals(2, result.media.size)
            val episode = result.media.single { it.isEpisode }
            assertEquals("Example", episode.info.title)
            assertEquals(3, episode.info.episode)
            assertEquals(1, result.warnings.size)
        } finally { directory.toFile().deleteRecursively() }
    }
}
