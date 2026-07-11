package me.xdan.aperture.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.data.local.dao.MediaDao
import me.xdan.aperture.data.local.dao.PlaybackProgressDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE playback_progress ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE playback_progress ADD COLUMN completedAt INTEGER DEFAULT NULL"
            )
            db.execSQL(
                "UPDATE playback_progress " +
                    "SET isCompleted = 1, completedAt = lastUpdated " +
                    "WHERE duration > 0 AND position >= duration * 0.95"
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMediaDao(db: AppDatabase): MediaDao {
        return db.mediaDao()
    }

    @Provides
    fun providePlaybackProgressDao(db: AppDatabase): PlaybackProgressDao {
        return db.playbackProgressDao()
    }
}
