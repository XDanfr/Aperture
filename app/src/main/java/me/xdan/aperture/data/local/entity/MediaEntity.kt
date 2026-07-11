package me.xdan.aperture.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media",
    indices = [Index(value = ["filePath"], unique = true)]
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val title: String,
    val year: Int? = null,
    val tmdbId: Int? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    val metadataAttemptedAt: Long? = null,
    val type: String, // "MOVIE", "TV_SHOW", "EPISODE"
    val duration: Long? = null,
    val lastPlayedPosition: Long? = null,
    val isFavorite: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis()
)
