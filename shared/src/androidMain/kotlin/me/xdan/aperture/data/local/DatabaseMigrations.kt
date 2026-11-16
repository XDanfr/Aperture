package me.xdan.aperture.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Schema history for installs that predate the current version. Desktop starts at 8 (DesktopMigrations). */
object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE media ADD COLUMN metadataAttemptedAt INTEGER DEFAULT NULL")
        }
    }

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

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE media ADD COLUMN seasonNumber INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE media ADD COLUMN episodeNumber INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE media ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE media ADD COLUMN favoriteAddedAt INTEGER DEFAULT NULL")
            db.execSQL("UPDATE media SET favoriteAddedAt = dateAdded WHERE isFavorite = 1")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE media ADD COLUMN episodeTitle TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE media ADD COLUMN episodeOverview TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE media ADD COLUMN stillPath TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE playback_progress ADD COLUMN keepInContinueWatching INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "UPDATE playback_progress SET keepInContinueWatching = 1 " +
                    "WHERE duration > 0 AND position >= duration * 0.05 AND position < duration * 0.95"
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE media ADD COLUMN sourceRootUri TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE media ADD COLUMN parentDocumentUri TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS artwork_palettes (artworkPath TEXT NOT NULL, seedArgb INTEGER NOT NULL, extractionVersion INTEGER NOT NULL, PRIMARY KEY(artworkPath))")
        }
    }

    val MIGRATION_8_9 = object : Migration(ProfileMigration.FROM, ProfileMigration.TO) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ProfileMigration.statements().forEach(db::execSQL)
        }
    }

    val MIGRATION_9_10 = object : Migration(JellyfinMigration.FROM, JellyfinMigration.TO) {
        override fun migrate(db: SupportSQLiteDatabase) {
            JellyfinMigration.statements.forEach(db::execSQL)
        }
    }

    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        MIGRATION_9_10
    )
}
