package me.xdan.aperture.data.local

/**
 * Version 9 → 10: Jellyfin. Each profile gets a library mode (local until the user picks
 * otherwise), plus tables for a profile's Jellyfin account, its server's libraries and the index of
 * the server's movies, shows and episodes. Nothing existing changes.
 */
object JellyfinMigration {
    const val FROM = 9
    const val TO = 10

    val statements: List<String> = listOf(
        "ALTER TABLE `profiles` ADD COLUMN `libraryMode` TEXT NOT NULL DEFAULT 'LOCAL'",
        "CREATE TABLE IF NOT EXISTS `jellyfin_accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` INTEGER NOT NULL, `serverId` TEXT NOT NULL, `serverName` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `serverVersion` TEXT, `userId` TEXT NOT NULL, `userName` TEXT NOT NULL, `isAdministrator` INTEGER NOT NULL, `lastFullSyncAt` INTEGER, `lastSyncAt` INTEGER, `createdAt` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_jellyfin_accounts_profileId` ON `jellyfin_accounts` (`profileId`)",
        "CREATE TABLE IF NOT EXISTS `remote_libraries` (`accountId` INTEGER NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `collectionType` TEXT, `sortIndex` INTEGER NOT NULL, `primaryImageTag` TEXT, PRIMARY KEY(`accountId`, `id`))",
        "CREATE TABLE IF NOT EXISTS `remote_items` (`accountId` INTEGER NOT NULL, `itemId` TEXT NOT NULL, `type` TEXT NOT NULL, `libraryId` TEXT, `name` TEXT NOT NULL, `sortName` TEXT, `year` INTEGER, `premiereDate` INTEGER, `overview` TEXT, `tmdbId` TEXT, `imdbId` TEXT, `tvdbId` TEXT, `seriesId` TEXT, `seriesName` TEXT, `seasonId` TEXT, `seasonNumber` INTEGER, `episodeNumber` INTEGER, `dateCreated` INTEGER, `communityRating` REAL, `officialRating` TEXT, `runtimeMs` INTEGER, `genres` TEXT, `primaryImageTag` TEXT, `backdropImageTag` TEXT, `backdropItemId` TEXT, `thumbImageTag` TEXT, `seriesPrimaryImageTag` TEXT, `width` INTEGER, `height` INTEGER, `container` TEXT, `mediaSourceCount` INTEGER NOT NULL, `played` INTEGER NOT NULL, `playbackPositionMs` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `lastPlayedAt` INTEGER, `syncGeneration` INTEGER NOT NULL, PRIMARY KEY(`accountId`, `itemId`))",
        "CREATE INDEX IF NOT EXISTS `index_remote_items_accountId_type_sortName` ON `remote_items` (`accountId`, `type`, `sortName`)",
        "CREATE INDEX IF NOT EXISTS `index_remote_items_accountId_libraryId` ON `remote_items` (`accountId`, `libraryId`)",
        "CREATE INDEX IF NOT EXISTS `index_remote_items_accountId_seriesId` ON `remote_items` (`accountId`, `seriesId`)",
        "CREATE INDEX IF NOT EXISTS `index_remote_items_tmdbId` ON `remote_items` (`tmdbId`)",
        "CREATE INDEX IF NOT EXISTS `index_remote_items_imdbId` ON `remote_items` (`imdbId`)",
        "CREATE INDEX IF NOT EXISTS `index_remote_items_tvdbId` ON `remote_items` (`tvdbId`)"
    )
}
