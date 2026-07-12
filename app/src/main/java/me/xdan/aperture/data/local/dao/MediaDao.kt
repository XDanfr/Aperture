package me.xdan.aperture.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.xdan.aperture.data.local.entity.MediaEntity

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE isHidden = 0 ORDER BY dateAdded DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY dateAdded DESC")
    suspend fun getAllMediaOnce(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE type = :type AND isHidden = 0 ORDER BY title COLLATE NOCASE ASC")
    fun getMediaByType(type: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isFavorite = 1 AND isHidden = 0 ORDER BY favoriteAddedAt DESC, dateAdded DESC")
    fun getFavoriteMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isHidden = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getHiddenMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getMediaById(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE type = 'EPISODE' AND title = :showTitle AND isHidden = 0 ORDER BY seasonNumber ASC, episodeNumber ASC, filePath ASC")
    suspend fun getEpisodesForShow(showTitle: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE filePath = :filePath")
    suspend fun getMediaByPath(filePath: String): MediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaEntity): Long

    @Update
    suspend fun updateMedia(media: MediaEntity)

    @Delete
    suspend fun deleteMedia(media: MediaEntity)

    @Query("SELECT * FROM media WHERE isHidden = 0 AND title LIKE '%' || :query || '%'")
    fun searchMedia(query: String): Flow<List<MediaEntity>>

    @Query("UPDATE media SET isFavorite = :isFavorite, favoriteAddedAt = CASE WHEN :isFavorite = 1 THEN :changedAt ELSE NULL END WHERE id = :mediaId")
    suspend fun setFavorite(mediaId: Long, isFavorite: Boolean, changedAt: Long)

    @Query("UPDATE media SET isHidden = :isHidden WHERE id = :mediaId")
    suspend fun setHidden(mediaId: Long, isHidden: Boolean)
}
