package me.xdan.aperture.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity

@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE mediaId = :mediaId")
    suspend fun getProgressForMedia(mediaId: Long): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress ORDER BY lastUpdated DESC")
    fun getAllProgress(): Flow<List<PlaybackProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE mediaId = :mediaId")
    suspend fun deleteProgress(mediaId: Long)
}
