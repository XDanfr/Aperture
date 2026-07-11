package me.xdan.aperture.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.xdan.aperture.data.local.entity.MediaEntity

@Dao
interface MediaDao {
    @Query("SELECT * FROM media ORDER BY dateAdded DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY dateAdded DESC")
    suspend fun getAllMediaOnce(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE type = :type ORDER BY dateAdded DESC")
    fun getMediaByType(type: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoriteMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getMediaById(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE filePath = :filePath")
    suspend fun getMediaByPath(filePath: String): MediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaEntity): Long

    @Update
    suspend fun updateMedia(media: MediaEntity)

    @Delete
    suspend fun deleteMedia(media: MediaEntity)

    @Query("SELECT * FROM media WHERE title LIKE '%' || :query || '%'")
    fun searchMedia(query: String): Flow<List<MediaEntity>>

    @Query("UPDATE media SET isFavorite = :isFavorite WHERE id = :mediaId")
    suspend fun setFavorite(mediaId: Long, isFavorite: Boolean)
}
