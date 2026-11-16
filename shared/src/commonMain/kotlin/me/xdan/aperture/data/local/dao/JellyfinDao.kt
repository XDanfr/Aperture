package me.xdan.aperture.data.local.dao

import androidx.room.*
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

    @Query("SELECT * FROM remote_items WHERE accountId = :accountId AND itemId = :itemId")
    suspend fun item(accountId: Long, itemId: String): RemoteItemEntity?

    @Query("SELECT * FROM remote_items WHERE accountId = :accountId AND type = :type ORDER BY COALESCE(sortName, name) COLLATE NOCASE")
    fun itemsOfType(accountId: Long, type: String): Flow<List<RemoteItemEntity>>

    @Query("SELECT COUNT(*) FROM remote_items WHERE accountId = :accountId AND type = :type")
    suspend fun countOfType(accountId: Long, type: String): Int

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
