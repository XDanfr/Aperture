package me.xdan.aperture.data.repository

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.xdan.aperture.BuildConfig
import me.xdan.aperture.data.local.dao.MediaDao
import me.xdan.aperture.data.local.dao.PlaybackProgressDao
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.LibraryPreparationProgress
import me.xdan.aperture.domain.repository.LibraryPreparationStage
import me.xdan.aperture.util.FilenameParser
import me.xdan.aperture.util.MediaScanner
import retrofit2.HttpException
import javax.inject.Inject

class MediaRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao,
    private val progressDao: PlaybackProgressDao,
    private val tmdbApi: TmdbApi,
    @ApplicationContext private val context: Context
) : MediaRepository {

    private val scanMutex = Mutex()
    private val _preparationProgress = MutableStateFlow(LibraryPreparationProgress())
    override val preparationProgress: StateFlow<LibraryPreparationProgress> = _preparationProgress

    override fun getAllMedia(): Flow<List<MediaEntity>> = mediaDao.getAllMedia()

    override fun getMediaByType(type: String): Flow<List<MediaEntity>> = mediaDao.getMediaByType(type)

    override suspend fun getMediaById(id: Long): MediaEntity? = mediaDao.getMediaById(id)

    override suspend fun insertMedia(media: MediaEntity): Long = mediaDao.insertMedia(media)

    override suspend fun updateMedia(media: MediaEntity) = mediaDao.updateMedia(media)

    override suspend fun deleteMedia(media: MediaEntity) = mediaDao.deleteMedia(media)

    override fun searchMedia(query: String): Flow<List<MediaEntity>> = mediaDao.searchMedia(query)

    override suspend fun getProgress(mediaId: Long): PlaybackProgressEntity? = progressDao.getProgressForMedia(mediaId)

    override fun getAllProgress(): Flow<List<PlaybackProgressEntity>> = progressDao.getAllProgress()

    override suspend fun saveProgress(progress: PlaybackProgressEntity) = progressDao.saveProgress(progress)

    override suspend fun scanLocalFiles() {
        scanMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    _preparationProgress.value = LibraryPreparationProgress(
                        stage = LibraryPreparationStage.DISCOVERING
                    )

                    val projection = arrayOf(
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.DATA,
                        MediaStore.Video.Media.DURATION
                    )

                    context.contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        null
                    )?.use {
                        val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                        val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                        while (it.moveToNext()) {
                            processFile(
                                name = it.getString(nameColumn),
                                path = it.getString(dataColumn),
                                duration = it.getLong(durationColumn)
                            )
                        }
                    }

                    MediaScanner.scanDirectories().forEach { file ->
                        processFile(file.name, file.absolutePath, 0L)
                    }

                    prepareMetadata(mediaDao.getAllMediaOnce())
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    _preparationProgress.value = _preparationProgress.value.copy(
                        stage = LibraryPreparationStage.ERROR,
                        currentTitle = null,
                        currentItemProgress = 0f,
                        errorMessage = exception.message ?: "Library preparation failed"
                    )
                }
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
            mediaDao.insertMedia(media)
        }
    }

    private suspend fun prepareMetadata(mediaItems: List<MediaEntity>) {
        var firstError: String? = null
        val initialPosters = mediaItems.mapNotNull { it.posterPath }.distinct()
        _preparationProgress.value = LibraryPreparationProgress(
            stage = LibraryPreparationStage.MATCHING,
            totalItems = mediaItems.size,
            posterPaths = initialPosters
        )

        mediaItems.forEachIndexed { index, media ->
            updateCurrentItem(media.title, 0.1f)

            val shouldRefreshMissingMatch = media.tmdbId == null && (
                media.metadataAttemptedAt == null ||
                    System.currentTimeMillis() - media.metadataAttemptedAt > METADATA_RETRY_INTERVAL_MS
                )

            val updated = if (shouldRefreshMissingMatch) {
                runCatching {
                    syncMetadataInternal(media) { progress ->
                        updateCurrentItem(media.title, progress)
                    }
                }.getOrElse { exception ->
                    exception.printStackTrace()
                    if (firstError == null) firstError = exception.message
                    media
                }
            } else {
                updateCurrentItem(media.title, 0.9f)
                media
            }

            val posters = (_preparationProgress.value.posterPaths + listOfNotNull(updated.posterPath))
                .distinct()
            _preparationProgress.value = _preparationProgress.value.copy(
                completedItems = index + 1,
                currentItemProgress = 1f,
                posterPaths = posters
            )
        }

        _preparationProgress.value = _preparationProgress.value.copy(
            stage = LibraryPreparationStage.COMPLETE,
            completedItems = mediaItems.size,
            currentTitle = null,
            currentItemProgress = 1f,
            errorMessage = firstError
        )
    }

    private fun updateCurrentItem(title: String, progress: Float) {
        _preparationProgress.value = _preparationProgress.value.copy(
            currentTitle = title,
            currentItemProgress = progress.coerceIn(0f, 1f)
        )
    }

    override suspend fun syncMetadata(media: MediaEntity) {
        withContext(Dispatchers.IO) {
            runCatching { syncMetadataInternal(media) }.onFailure { it.printStackTrace() }
        }
    }

    private suspend fun syncMetadataInternal(
        media: MediaEntity,
        onProgress: (Float) -> Unit = {}
    ): MediaEntity {
        check(BuildConfig.TMDB_API_KEY.isNotBlank()) { "TMDB API key is not configured" }

        onProgress(0.3f)
        val response = searchWithRetry(media)
        onProgress(0.75f)
        val result = response.results.firstOrNull()
        val updatedMedia = if (result == null) {
            media.copy(metadataAttemptedAt = System.currentTimeMillis())
        } else {
            media.copy(
                tmdbId = result.id,
                posterPath = result.posterPath,
                backdropPath = result.backdropPath,
                overview = result.overview,
                title = result.title ?: result.name ?: media.title,
                metadataAttemptedAt = System.currentTimeMillis()
            )
        }
        mediaDao.updateMedia(updatedMedia)
        onProgress(0.95f)
        return updatedMedia
    }

    private suspend fun searchWithRetry(media: MediaEntity): me.xdan.aperture.data.remote.dto.TmdbSearchResponse {
        repeat(MAX_METADATA_ATTEMPTS - 1) { attempt ->
            try {
                return searchTmdb(media)
            } catch (exception: HttpException) {
                if (exception.code() != 429) throw exception
                val retryAfterSeconds = exception.response()?.headers()?.get("Retry-After")
                    ?.toLongOrNull()
                delay((retryAfterSeconds?.times(1_000L)) ?: (1_000L shl attempt))
            }
        }
        return searchTmdb(media)
    }

    private suspend fun searchTmdb(media: MediaEntity) = if (media.type == "MOVIE") {
        tmdbApi.searchMovie(media.title, media.year, BuildConfig.TMDB_API_KEY)
    } else {
        tmdbApi.searchTvShow(media.title, media.year, BuildConfig.TMDB_API_KEY)
    }

    private companion object {
        const val MAX_METADATA_ATTEMPTS = 3
        const val METADATA_RETRY_INTERVAL_MS = 7L * 24L * 60L * 60L * 1_000L
    }
}
