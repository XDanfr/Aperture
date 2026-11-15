package me.xdan.aperture.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity

@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun getProgressForMedia(profileId: Long, mediaId: Long): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId ORDER BY lastUpdated DESC")
    fun getAllProgress(profileId: Long): Flow<List<PlaybackProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun deleteProgress(profileId: Long, mediaId: Long)
}
