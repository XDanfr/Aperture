package me.xdan.aperture.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.xdan.aperture.data.local.entity.MediaRecord
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.data.repository.ProfileRepositoryImpl
import me.xdan.aperture.data.repository.UserPreferencesRepositoryImpl
import okio.Path.Companion.toOkioPath
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileMigrationTest {
    @get:Rule val temp = TemporaryFolder()

    private fun schema(version: Int) =
        JSONObject(File("schemas/me.xdan.aperture.data.local.AppDatabase/$version.json").readText())
            .getJSONObject("database")

    /** Writes a version 8 database exactly as Room created it, with some saved state. */
    private fun createVersion8(path: String) {
        val connection = BundledSQLiteDriver().open(path)
        val database = schema(8)
        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            connection.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                connection.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = database.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) connection.execSQL(setup.getString(i))
        fun media(id: Int, title: String, type: String, favorite: Int, favoriteAt: String, hidden: Int, season: String = "NULL", episode: String = "NULL") =
            connection.execSQL(
                "INSERT INTO media (id, filePath, title, type, seasonNumber, episodeNumber, isFavorite, favoriteAddedAt, isHidden, dateAdded) " +
                    "VALUES ($id, '/m/$id.mkv', '$title', '$type', $season, $episode, $favorite, $favoriteAt, $hidden, $id)"
            )
        media(1, "Saved Movie", "MOVIE", 1, "500", 0)
        media(2, "Hidden Movie", "MOVIE", 0, "NULL", 1)
        media(3, "Plain Movie", "MOVIE", 0, "NULL", 0)
        media(4, "Show", "EPISODE", 1, "700", 0, "1", "1")
        media(5, "Show", "EPISODE", 1, "700", 0, "1", "2")
        connection.execSQL("INSERT INTO playback_progress VALUES (3, 1000, 4000, 900, 0, NULL, 1)")
        connection.execSQL("PRAGMA user_version = 8")
        connection.close()
    }

    private fun open(path: String) = Room.databaseBuilder<AppDatabase>(path)
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*DesktopMigrations.ALL_MIGRATIONS)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @Test
    fun `existing state becomes the first profile's`() = runBlocking {
        val path = File(temp.root, "aperture_db").absolutePath
        createVersion8(path)
        val database = open(path)
        try {
            val profiles = database.profileDao().getProfilesOnce()
            assertEquals(listOf(1L), profiles.map { it.id })
            assertEquals(ProfileMigration.DEFAULT_PROFILE_NAME, profiles.single().name)

            val media = database.mediaDao()
            assertEquals(listOf(1L, 4L, 5L), media.getFavoriteMedia(1).first().map { it.id }.sorted())
            assertEquals(500L, media.getMediaById(1, 1)?.favoriteAddedAt)
            assertEquals(listOf(2L), media.getHiddenMedia(1).first().map { it.id })
            assertFalse(media.getAllMedia(1).first().any { it.id == 2L })
            assertEquals(5, media.getAllMediaOnce().size)

            val progress = database.playbackProgressDao().getProgressForMedia(1, 3)
            assertEquals(1000L, progress?.position)
            assertTrue(progress!!.keepInContinueWatching)
        } finally {
            database.close()
        }
    }

    @Test
    fun `profiles keep separate lists, hidden items and progress`() = runBlocking {
        val path = File(temp.root, "aperture_db").absolutePath
        createVersion8(path)
        val database = open(path)
        val preferences = UserPreferencesRepositoryImpl(
            PreferenceDataStoreFactory.createWithPath { File(temp.root, "prefs.preferences_pb").toOkioPath() }
        )
        val profiles = ProfileRepositoryImpl(database.profileDao(), preferences)
        try {
            val second = profiles.createProfile("Sam", "green")
            assertEquals("green", preferences.themeIdFor(second).first())
            assertEquals("purple", preferences.themeIdFor(1).first())

            val media = database.mediaDao()
            // A new profile starts with nothing saved and sees everything.
            assertTrue(media.getFavoriteMedia(second).first().isEmpty())
            assertEquals(5, media.getAllMedia(second).first().size)
            assertNull(database.playbackProgressDao().getProgressForMedia(second, 3))

            media.setFavorite(second, 3, true, 900)
            media.setHidden(second, 1, true)
            database.playbackProgressDao().saveProgress(PlaybackProgressEntity(mediaId = 3, position = 10, duration = 4000, profileId = second))

            assertEquals(listOf(3L), media.getFavoriteMedia(second).first().map { it.id })
            assertFalse(media.getFavoriteMedia(1).first().any { it.id == 3L })
            assertTrue(media.getAllMedia(1).first().any { it.id == 1L })
            assertEquals(1000L, database.playbackProgressDao().getProgressForMedia(1, 3)?.position)
            assertEquals(10L, database.playbackProgressDao().getProgressForMedia(second, 3)?.position)

            // Settings follow the active profile.
            preferences.setThemeId("red")
            assertEquals("red", preferences.themeId.first())
            profiles.selectProfile(second)
            assertEquals(second, preferences.activeProfileId.first())
            assertEquals("green", preferences.themeId.first())

            // Deleting the profile takes its state with it and falls back to another profile.
            profiles.deleteProfile(second)
            assertEquals(1L, preferences.activeProfileId.first())
            assertEquals("red", preferences.themeId.first())
            assertNull(database.playbackProgressDao().getProgressForMedia(second, 3))
            assertTrue(media.getHiddenMedia(second).first().isEmpty())

            // The last profile stays.
            profiles.deleteProfile(1)
            assertEquals(1, database.profileDao().getProfilesOnce().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `deleting a file clears every profile's state for it`() = runBlocking {
        val path = File(temp.root, "aperture_db").absolutePath
        createVersion8(path)
        val database = open(path)
        try {
            val media = database.mediaDao()
            media.setFavorite(2, 1, true, 1)
            media.deleteMedia(1)
            assertNull(media.getRecordById(1))
            assertTrue(media.getFavoriteMedia(1).first().none { it.id == 1L })
            assertTrue(media.getFavoriteMedia(2).first().isEmpty())
            media.insertMedia(MediaRecord(filePath = "/m/new.mkv", title = "New", type = "MOVIE"))
            assertEquals(5, media.getAllMediaOnce().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `a fresh install gets a first profile`() = runBlocking {
        val database = Room.databaseBuilder<AppDatabase>(File(temp.root, "fresh_db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        val preferences = UserPreferencesRepositoryImpl(
            PreferenceDataStoreFactory.createWithPath { File(temp.root, "fresh.preferences_pb").toOkioPath() }
        )
        val profiles = ProfileRepositoryImpl(database.profileDao(), preferences)
        try {
            assertEquals(listOf(1L), profiles.profiles.first().map { it.id })
            profiles.resolveLaunchChoice()
            assertTrue(profiles.profileChosen.value)
        } finally {
            database.close()
        }
    }
}
