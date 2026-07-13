package me.xdan.aperture.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.data.remote.dto.TmdbResult

interface MediaRepository {
    val preparationProgress: StateFlow<LibraryPreparationProgress>
    val mediaFolders: StateFlow<List<MediaFolder>>

    fun getAllMedia(): Flow<List<MediaEntity>>
    fun getMediaByType(type: String): Flow<List<MediaEntity>>
    fun getFavoriteMedia(): Flow<List<MediaEntity>>
    fun getHiddenMedia(): Flow<List<MediaEntity>>
    suspend fun getMediaById(id: Long): MediaEntity?
    suspend fun getEpisodesForShow(showTitle: String): List<MediaEntity>
    suspend fun insertMedia(media: MediaEntity): Long
    suspend fun updateMedia(media: MediaEntity)
    suspend fun setFavorite(mediaId: Long, isFavorite: Boolean)
    suspend fun setHidden(mediaId: Long, isHidden: Boolean)
    suspend fun deleteMedia(media: MediaEntity)
    fun searchMedia(query: String): Flow<List<MediaEntity>>
    
    suspend fun getProgress(mediaId: Long): PlaybackProgressEntity?
    fun getAllProgress(): Flow<List<PlaybackProgressEntity>>
    suspend fun saveProgress(progress: PlaybackProgressEntity)
    suspend fun clearProgress(mediaId: Long)
    
    suspend fun scanLocalFiles()
    suspend fun addMediaFolder(uri: String): Result<Unit>
    suspend fun removeMediaFolder(uri: String)
    suspend fun syncMetadata(media: MediaEntity)
    suspend fun searchMetadataCandidates(media: MediaEntity, query: String? = null): List<TmdbResult>
    suspend fun applyMetadataCandidate(mediaId: Long, candidate: TmdbResult)
}

data class MediaFolder(
    val uri: String,
    val name: String,
    val isAvailable: Boolean
)
