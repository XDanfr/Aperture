package me.xdan.aperture.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import me.xdan.aperture.data.local.dao.MediaDao
import me.xdan.aperture.data.local.dao.PlaybackProgressDao
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity

@Database(
    entities = [MediaEntity::class, PlaybackProgressEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun playbackProgressDao(): PlaybackProgressDao

    companion object {
        const val DATABASE_NAME = "aperture_db"
    }
}
