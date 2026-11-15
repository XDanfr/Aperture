package me.xdan.aperture.data.local

/**
 * Version 8 → 9: profiles. My List and hidden state move from `media` into
 * `profile_media_state`, and `playback_progress` is keyed by profile. Everything that existed
 * before becomes profile 1's.
 *
 * SQLite on Android 8.1 can't drop columns, so `media` and `playback_progress` are rebuilt.
 * Each platform wraps these statements in its own Room migration type.
 */
object ProfileMigration {
    const val FROM = 8
    const val TO = 9
    const val DEFAULT_PROFILE_NAME = "Main"
    const val DEFAULT_PROFILE_COLOR = "purple"

    fun statements(now: Long = System.currentTimeMillis()): List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `colorId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL)",
        "INSERT OR IGNORE INTO `profiles` (`id`, `name`, `colorId`, `createdAt`, `lastUsedAt`) VALUES (1, '$DEFAULT_PROFILE_NAME', '$DEFAULT_PROFILE_COLOR', $now, $now)",

        "CREATE TABLE IF NOT EXISTS `profile_media_state` (`profileId` INTEGER NOT NULL, `mediaId` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `favoriteAddedAt` INTEGER, `isHidden` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `mediaId`))",
        "CREATE INDEX IF NOT EXISTS `index_profile_media_state_mediaId` ON `profile_media_state` (`mediaId`)",
        "INSERT INTO `profile_media_state` (`profileId`, `mediaId`, `isFavorite`, `favoriteAddedAt`, `isHidden`) SELECT 1, `id`, `isFavorite`, `favoriteAddedAt`, `isHidden` FROM `media` WHERE `isFavorite` = 1 OR `isHidden` = 1",

        "CREATE TABLE IF NOT EXISTS `media_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `filePath` TEXT NOT NULL, `sourceRootUri` TEXT, `parentDocumentUri` TEXT, `title` TEXT NOT NULL, `year` INTEGER, `tmdbId` INTEGER, `posterPath` TEXT, `backdropPath` TEXT, `overview` TEXT, `metadataAttemptedAt` INTEGER, `type` TEXT NOT NULL, `seasonNumber` INTEGER, `episodeNumber` INTEGER, `episodeTitle` TEXT, `episodeOverview` TEXT, `stillPath` TEXT, `duration` INTEGER, `lastPlayedPosition` INTEGER, `dateAdded` INTEGER NOT NULL)",
        "INSERT INTO `media_new` SELECT `id`, `filePath`, `sourceRootUri`, `parentDocumentUri`, `title`, `year`, `tmdbId`, `posterPath`, `backdropPath`, `overview`, `metadataAttemptedAt`, `type`, `seasonNumber`, `episodeNumber`, `episodeTitle`, `episodeOverview`, `stillPath`, `duration`, `lastPlayedPosition`, `dateAdded` FROM `media`",
        "DROP TABLE `media`",
        "ALTER TABLE `media_new` RENAME TO `media`",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_media_filePath` ON `media` (`filePath`)",

        "CREATE TABLE IF NOT EXISTS `playback_progress_new` (`mediaId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `completedAt` INTEGER, `keepInContinueWatching` INTEGER NOT NULL, `profileId` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `mediaId`))",
        "INSERT INTO `playback_progress_new` SELECT `mediaId`, `position`, `duration`, `lastUpdated`, `isCompleted`, `completedAt`, `keepInContinueWatching`, 1 FROM `playback_progress`",
        "DROP TABLE `playback_progress`",
        "ALTER TABLE `playback_progress_new` RENAME TO `playback_progress`",
        "CREATE INDEX IF NOT EXISTS `index_playback_progress_mediaId` ON `playback_progress` (`mediaId`)"
    )
}
