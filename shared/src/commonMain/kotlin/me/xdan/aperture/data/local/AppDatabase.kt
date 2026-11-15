package me.xdan.aperture.data.local

import me.xdan.aperture.data.local.dao.ArtworkPaletteDao
import me.xdan.aperture.data.local.entity.ArtworkPaletteEntity
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import me.xdan.aperture.data.local.dao.MediaDao
import me.xdan.aperture.data.local.dao.PlaybackProgressDao
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity

@Database(
    entities = [MediaEntity::class, PlaybackProgressEntity::class, ArtworkPaletteEntity::class],
    version = 8,
    exportSchema = true
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun artworkPaletteDao(): ArtworkPaletteDao
    abstract fun mediaDao(): MediaDao
    abstract fun playbackProgressDao(): PlaybackProgressDao

    companion object {
        const val DATABASE_NAME = "aperture_db"
    }
}

// Room generates the actual object for each target.
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
