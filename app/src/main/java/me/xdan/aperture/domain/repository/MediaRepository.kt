package me.xdan.aperture.domain.repository

import kotlinx.coroutines.flow.Flow
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity

interface MediaRepository {
    fun getAllMedia(): Flow<List<MediaEntity>>
    fun getMediaByType(type: String): Flow<List<MediaEntity>>
    suspend fun getMediaById(id: Long): MediaEntity?
    suspend fun insertMedia(media: MediaEntity): Long
    suspend fun updateMedia(media: MediaEntity)
    suspend fun deleteMedia(media: MediaEntity)
    fun searchMedia(query: String): Flow<List<MediaEntity>>
    
    suspend fun getProgress(mediaId: Long): PlaybackProgressEntity?
    suspend fun saveProgress(progress: PlaybackProgressEntity)
    
    suspend fun scanLocalFiles()
    suspend fun syncMetadata(media: MediaEntity)
}
