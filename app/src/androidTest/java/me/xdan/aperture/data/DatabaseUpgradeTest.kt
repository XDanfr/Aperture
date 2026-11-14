package me.xdan.aperture.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseUpgradeTest {
    @Test fun versionOneUpgradePreservesLibraryAndProgress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "upgrade-test.db"
        context.deleteDatabase(name)
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("CREATE TABLE media (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, filePath TEXT NOT NULL, title TEXT NOT NULL, year INTEGER, tmdbId INTEGER, posterPath TEXT, backdropPath TEXT, overview TEXT, type TEXT NOT NULL, duration INTEGER, lastPlayedPosition INTEGER, isFavorite INTEGER NOT NULL, dateAdded INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_media_filePath ON media(filePath)")
                db.execSQL("CREATE TABLE playback_progress (mediaId INTEGER PRIMARY KEY NOT NULL, position INTEGER NOT NULL, duration INTEGER NOT NULL, lastUpdated INTEGER NOT NULL)")
                db.execSQL("INSERT INTO media (id,filePath,title,type,isFavorite,dateAdded) VALUES (42,'/Movies/test.mp4','Test','MOVIE',1,123)")
                db.execSQL("INSERT INTO playback_progress VALUES (42,500,1000,123)")
                db.version = 1
            }
            // Use the production migration chain; Room validates the final schema on open.
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*DatabaseModule.ALL_MIGRATIONS).build()
            try {
                val sql = db.openHelper.writableDatabase
                sql.query("SELECT id,isFavorite,favoriteAddedAt FROM media").use { row ->
                    row.moveToFirst()
                    assertEquals(42L, row.getLong(0))
                    assertEquals(1, row.getInt(1))
                    assertEquals(123L, row.getLong(2))
                }
                sql.query("SELECT position,keepInContinueWatching FROM playback_progress").use { row ->
                    row.moveToFirst()
                    assertEquals(500L, row.getLong(0))
                    assertEquals(1, row.getInt(1))
                }
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
