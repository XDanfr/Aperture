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
import me.xdan.aperture.data.remote.dto.TmdbResult
import retrofit2.HttpException
import java.io.File
import java.io.IOException
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

    override fun getFavoriteMedia(): Flow<List<MediaEntity>> = mediaDao.getFavoriteMedia()

    override fun getHiddenMedia(): Flow<List<MediaEntity>> = mediaDao.getHiddenMedia()

    override suspend fun getMediaById(id: Long): MediaEntity? = mediaDao.getMediaById(id)

    override suspend fun insertMedia(media: MediaEntity): Long = mediaDao.insertMedia(media)

    override suspend fun updateMedia(media: MediaEntity) = mediaDao.updateMedia(media)

    override suspend fun setFavorite(mediaId: Long, isFavorite: Boolean) =
        mediaDao.setFavorite(mediaId, isFavorite, System.currentTimeMillis())

    override suspend fun setHidden(mediaId: Long, isHidden: Boolean) =
        mediaDao.setHidden(mediaId, isHidden)

    override suspend fun deleteMedia(media: MediaEntity) = mediaDao.deleteMedia(media)

    override fun searchMedia(query: String): Flow<List<MediaEntity>> = mediaDao.searchMedia(query)

    override suspend fun getProgress(mediaId: Long): PlaybackProgressEntity? = progressDao.getProgressForMedia(mediaId)

    override fun getAllProgress(): Flow<List<PlaybackProgressEntity>> = progressDao.getAllProgress()

    override suspend fun saveProgress(progress: PlaybackProgressEntity) = progressDao.saveProgress(progress)

    override suspend fun clearProgress(mediaId: Long) = progressDao.deleteProgress(mediaId)

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
        val cleanedInfo = FilenameParser.parse(name, path)
        if (existing == null) {
            val media = MediaEntity(
                filePath = path,
                title = cleanedInfo.title,
                year = cleanedInfo.year,
                duration = duration,
                type = if (cleanedInfo.season != null) "EPISODE" else "MOVIE",
                seasonNumber = cleanedInfo.season,
                episodeNumber = cleanedInfo.episode
            )
            mediaDao.insertMedia(media)
        } else if (
            cleanedInfo.season != null &&
            (existing.seasonNumber == null || existing.episodeNumber == null)
        ) {
            mediaDao.updateMedia(
                existing.copy(
                    title = cleanedInfo.title,
                    type = "EPISODE",
                    seasonNumber = cleanedInfo.season,
                    episodeNumber = cleanedInfo.episode,
                    tmdbId = null,
                    posterPath = null,
                    backdropPath = null,
                    overview = null,
                    metadataAttemptedAt = null
                )
            )
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

    override suspend fun searchMetadataCandidates(media: MediaEntity): List<TmdbResult> =
        withContext(Dispatchers.IO) {
            val lookupMedia = mediaForMetadataLookup(media)
            searchWithRetry(lookupMedia).results
                .sortedByDescending { metadataMatchScore(lookupMedia, it) }
                .take(20)
        }

    override suspend fun applyMetadataCandidate(mediaId: Long, candidate: TmdbResult) {
        val media = mediaDao.getMediaById(mediaId) ?: return
        val updated = media.copy(
            tmdbId = candidate.id,
            title = candidate.title ?: candidate.name ?: media.title,
            posterPath = candidate.posterPath,
            backdropPath = candidate.backdropPath,
            overview = candidate.overview,
            year = (candidate.releaseDate ?: candidate.firstAirDate)
                ?.take(4)?.toIntOrNull() ?: media.year,
            metadataAttemptedAt = System.currentTimeMillis()
        )
        mediaDao.updateMedia(updated)
    }

    private suspend fun syncMetadataInternal(
        media: MediaEntity,
        onProgress: (Float) -> Unit = {}
    ): MediaEntity {
        check(BuildConfig.TMDB_API_KEY.isNotBlank()) { "TMDB API key is not configured" }

        onProgress(0.3f)
        val lookupMedia = mediaForMetadataLookup(media)
        val response = searchWithRetry(lookupMedia)
        onProgress(0.75f)
        val result = response.results.maxByOrNull { metadataMatchScore(lookupMedia, it) }
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

    private fun mediaForMetadataLookup(media: MediaEntity): MediaEntity {
        val parsed = runCatching {
            FilenameParser.parse(File(media.filePath).name, media.filePath)
        }.getOrNull() ?: return media
        return media.copy(
            title = parsed.title.ifBlank { media.title },
            year = parsed.year ?: media.year,
            type = if (parsed.season != null) "EPISODE" else media.type
        )
    }

    private suspend fun searchWithRetry(media: MediaEntity): me.xdan.aperture.data.remote.dto.TmdbSearchResponse {
        var lastFailure: Exception? = null
        repeat(MAX_METADATA_ATTEMPTS) { attempt ->
            try {
                return searchTmdb(media)
            } catch (exception: HttpException) {
                val retryable = exception.code() == 429 || exception.code() in 500..599
                if (!retryable || attempt == MAX_METADATA_ATTEMPTS - 1) throw exception
                lastFailure = exception
                val retryAfterMillis = exception.response()?.headers()?.get("Retry-After")
                    ?.toLongOrNull()
                    ?.times(1_000L)
                delay(retryAfterMillis ?: (1_000L shl attempt))
            } catch (exception: IOException) {
                if (attempt == MAX_METADATA_ATTEMPTS - 1) throw exception
                lastFailure = exception
                delay(1_000L shl attempt)
            }
        }
        throw lastFailure ?: IOException("TMDB metadata request failed")
    }

    private suspend fun searchTmdb(media: MediaEntity) = if (media.type == "MOVIE") {
        tmdbApi.searchMovie(media.title, media.year, BuildConfig.TMDB_API_KEY)
    } else {
        tmdbApi.searchTvShow(media.title, media.year, BuildConfig.TMDB_API_KEY)
    }

    private fun metadataMatchScore(media: MediaEntity, result: TmdbResult): Int {
        val candidateTitle = result.title ?: result.name ?: return Int.MIN_VALUE
        val query = normaliseTitle(media.title)
        val candidate = normaliseTitle(candidateTitle)
        val queryTokens = query.split(' ').filter { it.isNotBlank() }.toSet()
        val candidateTokens = candidate.split(' ').filter { it.isNotBlank() }.toSet()
        val overlap = queryTokens.intersect(candidateTokens).size
        val union = queryTokens.union(candidateTokens).size.coerceAtLeast(1)
        val queryNumbers = NUMBER_TOKEN_REGEX.findAll(query).map { it.value }.toSet()
        val candidateNumbers = NUMBER_TOKEN_REGEX.findAll(candidate).map { it.value }.toSet()
        val candidateYear = (result.releaseDate ?: result.firstAirDate)
            ?.take(4)
            ?.toIntOrNull()

        var score = overlap * 100 / union
        if (query == candidate) score += 1_000
        if (candidate.startsWith(query) || query.startsWith(candidate)) score += 80
        score += when {
            queryNumbers == candidateNumbers -> 220
            queryNumbers.isNotEmpty() || candidateNumbers.isNotEmpty() -> -300
            else -> 0
        }
        if (media.year != null && candidateYear != null) {
            score += if (media.year == candidateYear) 140 else -80
        }
        return score
    }

    private fun normaliseTitle(title: String): String = title
        .lowercase()
        .replace(Regex("['’`´]"), "")
        .replace(NON_ALPHANUMERIC_REGEX, " ")
        .replace(MULTIPLE_SPACES_REGEX, " ")
        .trim()

    private companion object {
        const val MAX_METADATA_ATTEMPTS = 3
        const val METADATA_RETRY_INTERVAL_MS = 7L * 24L * 60L * 60L * 1_000L
        val NUMBER_TOKEN_REGEX = Regex("\\b\\d+\\b")
        val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]+")
        val MULTIPLE_SPACES_REGEX = Regex("\\s+")
    }
}
