package me.xdan.aperture.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey val mediaId: Long,
    val position: Long,
    val duration: Long,
    val lastUpdated: Long = System.currentTimeMillis()
)
