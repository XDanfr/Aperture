package me.xdan.aperture.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A media file and its metadata, shared by every profile. */
@Entity(
    tableName = "media",
    indices = [Index(value = ["filePath"], unique = true)]
)
data class MediaRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val sourceRootUri: String? = null,
    val parentDocumentUri: String? = null,
    val title: String,
    val year: Int? = null,
    val tmdbId: Int? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    val metadataAttemptedAt: Long? = null,
    val type: String, // "MOVIE", "TV_SHOW", "EPISODE"
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeOverview: String? = null,
    val stillPath: String? = null,
    val duration: Long? = null,
    val lastPlayedPosition: Long? = null,
    /** TMDB genres, "|"-separated. */
    val genres: String? = null,
    /** TMDB's average rating, 0–10. */
    val rating: Float? = null,
    /** The picture's height (2160 for 4K), from the file name or playback. */
    val videoHeight: Int? = null,
    /** When the file was found missing (its folder there, the file not); null while it's there. */
    val missingSince: Long? = null,
    val dateAdded: Long = System.currentTimeMillis()
) {
    /** As a profile with nothing saved for it would see it. */
    fun toEntity() = MediaEntity(
        id = id,
        filePath = filePath,
        sourceRootUri = sourceRootUri,
        parentDocumentUri = parentDocumentUri,
        title = title,
        year = year,
        tmdbId = tmdbId,
        posterPath = posterPath,
        backdropPath = backdropPath,
        overview = overview,
        metadataAttemptedAt = metadataAttemptedAt,
        type = type,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
        episodeOverview = episodeOverview,
        stillPath = stillPath,
        duration = duration,
        lastPlayedPosition = lastPlayedPosition,
        genres = genres,
        rating = rating,
        videoHeight = videoHeight,
        missingSince = missingSince,
        dateAdded = dateAdded
    )
}

/**
 * A media file as the active profile sees it: the shared [MediaRecord] columns plus that profile's
 * My List and hidden state from [ProfileMediaStateEntity]. Queries build it with a LEFT JOIN.
 */
data class MediaEntity(
    val id: Long = 0,
    val filePath: String,
    val sourceRootUri: String? = null,
    val parentDocumentUri: String? = null,
    val title: String,
    val year: Int? = null,
    val tmdbId: Int? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    val metadataAttemptedAt: Long? = null,
    val type: String, // "MOVIE", "TV_SHOW", "EPISODE"
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeOverview: String? = null,
    val stillPath: String? = null,
    val duration: Long? = null,
    val lastPlayedPosition: Long? = null,
    val isFavorite: Boolean = false,
    val favoriteAddedAt: Long? = null,
    val isHidden: Boolean = false,
    val genres: String? = null,
    val rating: Float? = null,
    val videoHeight: Int? = null,
    /** When the file was found missing (its folder there, the file not); null while it's there. */
    val missingSince: Long? = null,
    val dateAdded: Long = System.currentTimeMillis()
) {
    fun toRecord() = MediaRecord(
        id = id,
        filePath = filePath,
        sourceRootUri = sourceRootUri,
        parentDocumentUri = parentDocumentUri,
        title = title,
        year = year,
        tmdbId = tmdbId,
        posterPath = posterPath,
        backdropPath = backdropPath,
        overview = overview,
        metadataAttemptedAt = metadataAttemptedAt,
        type = type,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
        episodeOverview = episodeOverview,
        stillPath = stillPath,
        duration = duration,
        lastPlayedPosition = lastPlayedPosition,
        genres = genres,
        rating = rating,
        videoHeight = videoHeight,
        missingSince = missingSince,
        dateAdded = dateAdded
    )
}
