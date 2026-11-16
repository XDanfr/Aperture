package me.xdan.aperture.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A profile's Jellyfin sign-in: one user on one server. The access token is kept outside the
 * database (see JellyfinCredentials) so it never ends up in a device backup.
 */
@Entity(
    tableName = "jellyfin_accounts",
    indices = [Index(value = ["profileId"], unique = true)]
)
data class JellyfinAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val serverId: String,
    val serverName: String,
    val baseUrl: String,
    val serverVersion: String? = null,
    val userId: String,
    val userName: String,
    val isAdministrator: Boolean = false,
    /** Server time of the last completed full sync, as epoch ms. */
    val lastFullSyncAt: Long? = null,
    /** Server time of the last completed incremental sync, as epoch ms. */
    val lastSyncAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** A library ("user view") on the server, for the sidebar. */
@Entity(tableName = "remote_libraries", primaryKeys = ["accountId", "id"])
data class RemoteLibraryEntity(
    val accountId: Long,
    val id: String,
    val name: String,
    /** Jellyfin's collection type (movies, tvshows, boxsets, …), or null for mixed folders. */
    val collectionType: String? = null,
    val sortIndex: Int = 0,
    val primaryImageTag: String? = null
)

/**
 * The index of a server's movies, shows and episodes, enough to sort, filter and match against
 * local files without asking the server. Details and playback info are fetched when needed.
 */
@Entity(
    tableName = "remote_items",
    primaryKeys = ["accountId", "itemId"],
    indices = [
        Index(value = ["accountId", "type", "sortName"]),
        Index(value = ["accountId", "libraryId"]),
        Index(value = ["accountId", "seriesId"]),
        Index(value = ["tmdbId"]),
        Index(value = ["imdbId"]),
        Index(value = ["tvdbId"])
    ]
)
data class RemoteItemEntity(
    val accountId: Long,
    val itemId: String,
    /** MOVIE, SERIES, EPISODE or VIDEO (a home video). */
    val type: String,
    val libraryId: String? = null,
    val name: String,
    val sortName: String? = null,
    val year: Int? = null,
    val premiereDate: Long? = null,
    val overview: String? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val tvdbId: String? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seasonId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val dateCreated: Long? = null,
    val communityRating: Float? = null,
    val officialRating: String? = null,
    val runtimeMs: Long? = null,
    /** Genres joined with '|'. */
    val genres: String? = null,
    val primaryImageTag: String? = null,
    val backdropImageTag: String? = null,
    /** Where the backdrop comes from when the item has none of its own (an episode's series). */
    val backdropItemId: String? = null,
    val thumbImageTag: String? = null,
    val seriesPrimaryImageTag: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val container: String? = null,
    val mediaSourceCount: Int = 1,
    val played: Boolean = false,
    val playbackPositionMs: Long = 0,
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long? = null,
    /** Which full sync last saw this item; rows a full sync doesn't see are deleted. */
    val syncGeneration: Long = 0
)
