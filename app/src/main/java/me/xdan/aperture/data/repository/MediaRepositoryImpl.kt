package me.xdan.aperture.data.repository

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.xdan.aperture.BuildConfig
import me.xdan.aperture.data.local.dao.MediaDao
import me.xdan.aperture.data.local.dao.PlaybackProgressDao
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.util.FilenameParser
import me.xdan.aperture.util.MediaScanner
import javax.inject.Inject

class MediaRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao,
    private val progressDao: PlaybackProgressDao,
    private val tmdbApi: TmdbApi,
    @ApplicationContext private val context: Context
) : MediaRepository {

    override fun getAllMedia(): Flow<List<MediaEntity>> = mediaDao.getAllMedia()

    override fun getMediaByType(type: String): Flow<List<MediaEntity>> = mediaDao.getMediaByType(type)

    override suspend fun getMediaById(id: Long): MediaEntity? = mediaDao.getMediaById(id)

    override suspend fun insertMedia(media: MediaEntity): Long = mediaDao.insertMedia(media)

    override suspend fun updateMedia(media: MediaEntity) = mediaDao.updateMedia(media)

    override suspend fun deleteMedia(media: MediaEntity) = mediaDao.deleteMedia(media)

    override fun searchMedia(query: String): Flow<List<MediaEntity>> = mediaDao.searchMedia(query)

    override suspend fun getProgress(mediaId: Long): PlaybackProgressEntity? = progressDao.getProgressForMedia(mediaId)

    override suspend fun saveProgress(progress: PlaybackProgressEntity) = progressDao.saveProgress(progress)

    override suspend fun scanLocalFiles() {
        withContext(Dispatchers.IO) {
            // 1. MediaStore Scan
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION
            )
            
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            
            cursor?.use {
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                
                while (it.moveToNext()) {
                    val name = it.getString(nameColumn)
                    val path = it.getString(dataColumn)
                    val duration = it.getLong(durationColumn)
                    processFile(name, path, duration)
                }
            }

            // 2. Manual Directory Scan (Fallback for unindexed files)
            MediaScanner.scanDirectories().forEach { file ->
                processFile(file.name, file.absolutePath, 0L)
            }
        }
    }

    private suspend fun processFile(name: String, path: String, duration: Long) {
        val existing = mediaDao.getMediaByPath(path)
        if (existing == null) {
            val cleanedInfo = FilenameParser.parse(name)
            val media = MediaEntity(
                filePath = path,
                title = cleanedInfo.title,
                year = cleanedInfo.year,
                duration = duration,
                type = if (cleanedInfo.season != null) "EPISODE" else "MOVIE"
            )
            val id = mediaDao.insertMedia(media)
            // Trigger metadata sync immediately for new files
            syncMetadata(media.copy(id = id))
        } else if (existing.tmdbId == null) {
            // Retry sync if metadata is missing
            syncMetadata(existing)
        }
    }

    override suspend fun syncMetadata(media: MediaEntity) {
        withContext(Dispatchers.IO) {
            try {
                if (BuildConfig.TMDB_API_KEY.isBlank()) return@withContext

                val response = if (media.type == "MOVIE") {
                    tmdbApi.searchMovie(media.title, media.year, BuildConfig.TMDB_API_KEY)
                } else {
                    tmdbApi.searchTvShow(media.title, media.year, BuildConfig.TMDB_API_KEY)
                }
                
                val result = response.results.firstOrNull()
                if (result != null) {
                    val updatedMedia = media.copy(
                        tmdbId = result.id,
                        posterPath = result.posterPath,
                        backdropPath = result.backdropPath,
                        overview = result.overview,
                        title = result.title ?: result.name ?: media.title
                    )
                    mediaDao.updateMedia(updatedMedia)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
