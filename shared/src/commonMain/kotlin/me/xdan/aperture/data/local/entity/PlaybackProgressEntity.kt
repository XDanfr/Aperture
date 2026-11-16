package me.xdan.aperture.data.local.entity

import androidx.room.Entity

/** Where a profile got to in a media file. */
@Entity(
    tableName = "playback_progress",
    primaryKeys = ["profileId", "mediaId"],
    indices = [androidx.room.Index("mediaId")]
)
data class PlaybackProgressEntity(
    val mediaId: Long,
    val position: Long,
    val duration: Long,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val keepInContinueWatching: Boolean = false,
    /** The repository stamps the active profile on save, so callers can leave this alone. */
    val profileId: Long = ProfileEntity.DEFAULT_ID
)
