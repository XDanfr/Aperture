package me.xdan.aperture.data.local

import me.xdan.aperture.data.local.dao.ArtworkPaletteDao
import me.xdan.aperture.data.local.entity.ArtworkPaletteEntity
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import me.xdan.aperture.data.local.dao.MediaDao
import me.xdan.aperture.data.local.dao.PlaybackProgressDao
import me.xdan.aperture.data.local.entity.MediaRecord
import me.xdan.aperture.data.local.entity.ProfileEntity
import me.xdan.aperture.data.local.entity.ProfileMediaStateEntity
import me.xdan.aperture.data.local.dao.ProfileDao
import me.xdan.aperture.data.local.dao.JellyfinDao
import me.xdan.aperture.data.local.entity.JellyfinAccountEntity
import me.xdan.aperture.data.local.entity.RemoteItemEntity
import me.xdan.aperture.data.local.entity.RemoteLibraryEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity

@Database(
    entities = [
        MediaRecord::class, PlaybackProgressEntity::class, ArtworkPaletteEntity::class,
        ProfileEntity::class, ProfileMediaStateEntity::class,
        JellyfinAccountEntity::class, RemoteLibraryEntity::class, RemoteItemEntity::class
    ],
    version = 12,
    exportSchema = true
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun artworkPaletteDao(): ArtworkPaletteDao
    abstract fun mediaDao(): MediaDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun profileDao(): ProfileDao
    abstract fun jellyfinDao(): JellyfinDao

    companion object {
        const val DATABASE_NAME = "aperture_db"
    }
}

// Room generates the actual object for each target.
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
