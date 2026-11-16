package me.xdan.aperture.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.xdan.aperture.data.local.entity.ProfileEntity

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt ASC, id ASC")
    fun getProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC, id ASC")
    suspend fun getProfilesOnce(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfile(id: Long): ProfileEntity?

    @Insert
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Query("UPDATE profiles SET lastUsedAt = :at WHERE id = :id")
    suspend fun markUsed(id: Long, at: Long)

    @Query("UPDATE profiles SET libraryMode = :mode WHERE id = :id")
    suspend fun setLibraryMode(id: Long, mode: String)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfileRow(id: Long)

    @Query("DELETE FROM remote_items WHERE accountId IN (SELECT id FROM jellyfin_accounts WHERE profileId = :id)")
    suspend fun deleteRemoteItemsForProfile(id: Long)

    @Query("DELETE FROM remote_libraries WHERE accountId IN (SELECT id FROM jellyfin_accounts WHERE profileId = :id)")
    suspend fun deleteRemoteLibrariesForProfile(id: Long)

    @Query("DELETE FROM jellyfin_accounts WHERE profileId = :id")
    suspend fun deleteJellyfinAccountForProfile(id: Long)

    @Query("DELETE FROM profile_media_state WHERE profileId = :id")
    suspend fun deleteStateForProfile(id: Long)

    @Query("DELETE FROM playback_progress WHERE profileId = :id")
    suspend fun deleteProgressForProfile(id: Long)

    /** Removes a profile with its My List, hidden items, progress and Jellyfin account. */
    @Transaction
    suspend fun deleteProfile(id: Long) {
        deleteStateForProfile(id)
        deleteProgressForProfile(id)
        deleteRemoteItemsForProfile(id)
        deleteRemoteLibrariesForProfile(id)
        deleteJellyfinAccountForProfile(id)
        deleteProfileRow(id)
    }
}
