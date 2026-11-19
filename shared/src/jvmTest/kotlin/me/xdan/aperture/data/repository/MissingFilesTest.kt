package me.xdan.aperture.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.xdan.aperture.data.artwork.ArtworkPrefetcher
import me.xdan.aperture.data.jellyfin.JellyfinCredentials
import me.xdan.aperture.data.library.DiscoveredFile
import me.xdan.aperture.data.library.MediaLibrarySource
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.domain.repository.MediaFolder
import me.xdan.aperture.platform.PropertiesStores
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** A file that goes is marked missing, comes back under a new name or place, or is removed. */
class MissingFilesTest {
    private val temp: File = Files.createTempDirectory("missing-files").toFile()
    private val database = Room.databaseBuilder<AppDatabase>(File(temp, "db").absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    /** What the device's index lists, and which files have really gone. */
    private var files = listOf<DiscoveredFile>()
    private val gone = mutableSetOf<String>()

    private val source = object : MediaLibrarySource {
        override suspend fun discoverSystemMedia(onFile: suspend (DiscoveredFile) -> Unit) = files.forEach { onFile(it) }
        override suspend fun grantFolder(folder: String) = Unit
        override fun releaseFolder(folder: String) = Unit
        override suspend fun walkFolder(folder: String, onFile: suspend (DiscoveredFile) -> Unit) = false
        override fun describeFolder(folder: String) = MediaFolder(folder, folder, true)
        override fun fileName(location: String) = location.substringAfterLast('/')
        override fun parserPath(location: String): String? = location
        override fun isGone(location: String, parentLocation: String?) = location in gone
    }

    private val tmdb = java.lang.reflect.Proxy.newProxyInstance(TmdbApi::class.java.classLoader, arrayOf(TmdbApi::class.java)) { _, _, _ ->
        error("no network in tests")
    } as TmdbApi

    private val repository: MediaRepositoryImpl = runBlocking {
        val credentials = JellyfinCredentials(PropertiesStores(File(temp, "creds")))
        val preferences = UserPreferencesRepositoryImpl(
            PreferenceDataStoreFactory.createWithPath { File(temp, "prefs.preferences_pb").toOkioPath() }
        )
        val profiles = ProfileRepositoryImpl(database.profileDao(), preferences, database.jellyfinDao(), credentials)
        profiles.profiles.first()
        MediaRepositoryImpl(
            database.mediaDao(), database.playbackProgressDao(), tmdb,
            ArtworkPrefetcher(coil3.PlatformContext.INSTANCE, me.xdan.aperture.util.ScreenMetrics { 1920 to 1080 }) { error("no images") },
            source, PropertiesStores(File(temp, "stores")), profiles
        )
    }

    @After
    fun tearDown() {
        database.close()
        temp.deleteRecursively()
    }

    private fun record() = runBlocking { database.mediaDao().getAllMediaOnce().single() }

    @Test
    fun `a deleted file is marked missing and keeps its progress`() = runBlocking {
        files = listOf(DiscoveredFile("Heat (1995).mkv", "/films/Heat (1995).mkv"))
        repository.scanLocalFiles()
        val id = record().id
        repository.saveProgress(PlaybackProgressEntity(mediaId = id, position = 600_000, duration = 6_000_000))

        files = emptyList()
        gone += "/films/Heat (1995).mkv"
        repository.scanLocalFiles()

        assertNotNull(record().missingSince)
        assertEquals(600_000L, repository.getProgress(id)?.position)
        assertEquals(listOf(id), repository.getMissingMedia().first().map { it.id })
    }

    @Test
    fun `a moved or renamed file relinks to its title`() = runBlocking {
        files = listOf(DiscoveredFile("Heat (1995).mkv", "/films/Heat (1995).mkv"))
        repository.scanLocalFiles()
        val id = record().id
        files = emptyList()
        gone += "/films/Heat (1995).mkv"
        repository.scanLocalFiles()

        // Moved to another folder under the same name.
        files = listOf(DiscoveredFile("Heat (1995).mkv", "/archive/Heat (1995).mkv"))
        repository.scanLocalFiles()
        assertEquals(id, record().id)
        assertEquals("/archive/Heat (1995).mkv", record().filePath)
        assertNull(record().missingSince)

        // Renamed: the same title and year from a different file name.
        files = emptyList()
        gone += "/archive/Heat (1995).mkv"
        repository.scanLocalFiles()
        files = listOf(DiscoveredFile("Heat.1995.1080p.BluRay.mkv", "/archive/Heat.1995.1080p.BluRay.mkv"))
        repository.scanLocalFiles()
        assertEquals(id, record().id)
        assertNull(record().missingSince)
    }

    @Test
    fun `a file on a drive that's away isn't missing, and only the user removes one`() = runBlocking {
        files = listOf(DiscoveredFile("Heat (1995).mkv", "/films/Heat (1995).mkv"))
        repository.scanLocalFiles()
        val id = record().id
        // Not listed, but not known to be gone (an unplugged drive).
        files = emptyList()
        repository.scanLocalFiles()
        assertNull(record().missingSince)

        gone += "/films/Heat (1995).mkv"
        assertTrue(repository.markMissingIfGone(id))
        repository.removeMissing(id)
        assertTrue(database.mediaDao().getAllMediaOnce().isEmpty())
    }
}
