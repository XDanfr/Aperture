package me.xdan.aperture.data.local.dao

import androidx.room.*
import androidx.room.RoomRawQuery
import kotlinx.coroutines.flow.Flow
import me.xdan.aperture.data.local.entity.JellyfinAccountEntity
import me.xdan.aperture.data.local.entity.RemoteItemEntity
import me.xdan.aperture.data.local.entity.RemoteLibraryEntity

@Dao
interface JellyfinDao {
    // accounts

    @Query("SELECT * FROM jellyfin_accounts WHERE profileId = :profileId")
    fun accountForProfile(profileId: Long): Flow<JellyfinAccountEntity?>

    @Query("SELECT * FROM jellyfin_accounts WHERE profileId = :profileId")
    suspend fun accountForProfileOnce(profileId: Long): JellyfinAccountEntity?

    @Query("SELECT * FROM jellyfin_accounts WHERE id = :id")
    suspend fun account(id: Long): JellyfinAccountEntity?

    @Query("SELECT * FROM jellyfin_accounts")
    suspend fun accounts(): List<JellyfinAccountEntity>

    @Insert
    suspend fun insertAccount(account: JellyfinAccountEntity): Long

    @Update
    suspend fun updateAccount(account: JellyfinAccountEntity)

    @Query("UPDATE jellyfin_accounts SET lastSyncAt = :at WHERE id = :id")
    suspend fun markSynced(id: Long, at: Long)

    @Query("UPDATE jellyfin_accounts SET lastSyncAt = :at, lastFullSyncAt = :at WHERE id = :id")
    suspend fun markFullySynced(id: Long, at: Long)

    @Query("DELETE FROM jellyfin_accounts WHERE id = :id")
    suspend fun deleteAccountRow(id: Long)

    @Query("DELETE FROM remote_items WHERE accountId = :accountId")
    suspend fun deleteItemsForAccount(accountId: Long)

    @Query("DELETE FROM remote_libraries WHERE accountId = :accountId")
    suspend fun deleteLibrariesForAccount(accountId: Long)

    /** Signs out: the account and everything indexed for it. */
    @Transaction
    suspend fun deleteAccount(id: Long) {
        deleteItemsForAccount(id)
        deleteLibrariesForAccount(id)
        deleteAccountRow(id)
    }

    // libraries

    @Query("SELECT * FROM remote_libraries WHERE accountId = :accountId ORDER BY sortIndex")
    fun libraries(accountId: Long): Flow<List<RemoteLibraryEntity>>

    @Transaction
    suspend fun replaceLibraries(accountId: Long, libraries: List<RemoteLibraryEntity>) {
        deleteLibrariesForAccount(accountId)
        insertLibraries(libraries)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraries(libraries: List<RemoteLibraryEntity>)

    // items

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<RemoteItemEntity>)

    @Query("DELETE FROM remote_items WHERE accountId = :accountId AND itemId IN (:itemIds)")
    suspend fun deleteItems(accountId: Long, itemIds: List<String>)

    /** After a full sync: drops whatever the server no longer has. */
    @Query("DELETE FROM remote_items WHERE accountId = :accountId AND syncGeneration != :generation")
    suspend fun deleteItemsNotIn(accountId: Long, generation: Long): Int

    /** The server's favourites, which are My List for Jellyfin titles. */
    @Query("""
        SELECT * FROM remote_items
        WHERE accountId = :accountId AND isFavorite = 1 AND type IN ('MOVIE', 'SERIES', 'VIDEO')
        ORDER BY COALESCE(sortName, name) COLLATE NOCASE
    """)
    fun favorites(accountId: Long): Flow<List<RemoteItemEntity>>

    /**
     * Titles whose name matches, from typed libraries only: an untyped folder may be a JellyBridge
     * Discover library, whose placeholders aren't things you have.
     */
    @Query("""
        SELECT i.* FROM remote_items i
        JOIN remote_libraries l ON l.accountId = i.accountId AND l.id = i.libraryId
        WHERE i.accountId = :accountId AND i.type IN ('MOVIE', 'SERIES', 'VIDEO')
            AND l.collectionType IN ('movies', 'tvshows', 'homevideos')
            AND i.name LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY COALESCE(i.sortName, i.name) COLLATE NOCASE LIMIT :limit
    """)
    fun search(accountId: Long, query: String, limit: Int): Flow<List<RemoteItemEntity>>

    @Query("SELECT * FROM remote_items WHERE accountId = :accountId AND itemId = :itemId")
    suspend fun item(accountId: Long, itemId: String): RemoteItemEntity?

    @Query("SELECT * FROM remote_items WHERE accountId = :accountId AND type = :type ORDER BY COALESCE(sortName, name) COLLATE NOCASE")
    fun itemsOfType(accountId: Long, type: String): Flow<List<RemoteItemEntity>>

    @Query("SELECT COUNT(*) FROM remote_items WHERE accountId = :accountId AND type IN ('MOVIE', 'SERIES')")
    fun titleCount(accountId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM remote_items WHERE accountId = :accountId AND type = :type")
    suspend fun countOfType(accountId: Long, type: String): Int

    /** Library grids: sorting and filtering happen in SQLite (see JellyfinCatalog). */
    @RawQuery(observedEntities = [RemoteItemEntity::class])
    fun itemsRaw(query: RoomRawQuery): Flow<List<RemoteItemEntity>>

    @Query("""
        SELECT * FROM remote_items
        WHERE accountId = :accountId AND playbackPositionMs > 0 AND played = 0 AND type IN ('MOVIE', 'EPISODE', 'VIDEO')
        ORDER BY lastPlayedAt DESC LIMIT :limit
    """)
    fun continueWatching(accountId: Long, limit: Int): Flow<List<RemoteItemEntity>>

    @Query("""
        SELECT * FROM remote_items
        WHERE accountId = :accountId AND libraryId = :libraryId AND type IN ('MOVIE', 'SERIES', 'VIDEO')
        ORDER BY dateCreated DESC LIMIT :limit
    """)
    fun recentlyAdded(accountId: Long, libraryId: String, limit: Int): Flow<List<RemoteItemEntity>>

    /**
     * Unwatched films with a backdrop, only from movie libraries: mixed folders hold home clips typed
     * as Movie, and a JellyBridge Discover library holds placeholders for titles that aren't there.
     */
    @Query("""
        SELECT i.* FROM remote_items i
        JOIN remote_libraries l ON l.accountId = i.accountId AND l.id = i.libraryId
        WHERE i.accountId = :accountId AND i.type = 'MOVIE' AND i.backdropImageTag IS NOT NULL AND i.played = 0
            AND l.collectionType = 'movies'
        ORDER BY RANDOM() LIMIT :limit
    """)
    suspend fun spotlightCandidates(accountId: Long, limit: Int): List<RemoteItemEntity>

    @Query("SELECT * FROM remote_items WHERE accountId = :accountId AND itemId = :itemId")
    fun observeItem(accountId: Long, itemId: String): Flow<RemoteItemEntity?>

    @Query("SELECT * FROM remote_items WHERE accountId = :accountId AND seriesId = :seriesId AND type = 'EPISODE' ORDER BY seasonNumber, episodeNumber")
    fun observeEpisodes(accountId: Long, seriesId: String): Flow<List<RemoteItemEntity>>

    @Query("UPDATE remote_items SET isFavorite = :favorite WHERE accountId = :accountId AND itemId = :itemId")
    suspend fun setFavorite(accountId: Long, itemId: String, favorite: Boolean)

    @Query("UPDATE remote_items SET played = :played, playbackPositionMs = 0 WHERE accountId = :accountId AND itemId = :itemId")
    suspend fun setPlayed(accountId: Long, itemId: String, played: Boolean)

    @Query("SELECT * FROM remote_items WHERE accountId = :accountId AND seriesId = :seriesId ORDER BY seasonNumber, episodeNumber")
    suspend fun episodesOf(accountId: Long, seriesId: String): List<RemoteItemEntity>

    @Query("UPDATE remote_items SET played = :played, playbackPositionMs = :positionMs, playCount = :playCount, isFavorite = :isFavorite, lastPlayedAt = :lastPlayedAt WHERE accountId = :accountId AND itemId = :itemId")
    suspend fun updateUserData(
        accountId: Long,
        itemId: String,
        played: Boolean,
        positionMs: Long,
        playCount: Int,
        isFavorite: Boolean,
        lastPlayedAt: Long?
    )
}
