package me.xdan.aperture.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** The desktop app shipped at schema 8, so its history starts there. */
object DesktopMigrations {
    val MIGRATION_8_9 = object : Migration(ProfileMigration.FROM, ProfileMigration.TO) {
        override fun migrate(connection: SQLiteConnection) {
            ProfileMigration.statements().forEach(connection::execSQL)
        }
    }

    val MIGRATION_9_10 = object : Migration(JellyfinMigration.FROM, JellyfinMigration.TO) {
        override fun migrate(connection: SQLiteConnection) {
            JellyfinMigration.statements.forEach(connection::execSQL)
        }
    }

    val ALL_MIGRATIONS = arrayOf(MIGRATION_8_9, MIGRATION_9_10)
}
