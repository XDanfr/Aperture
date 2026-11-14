package me.xdan.aperture.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artwork_palettes")
data class ArtworkPaletteEntity(
    @PrimaryKey val artworkPath: String,
    val seedArgb: Int,
    val extractionVersion: Int
)
