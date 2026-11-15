package me.xdan.aperture.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.xdan.aperture.data.local.entity.ArtworkPaletteEntity

@Dao
interface ArtworkPaletteDao {
    @Query("SELECT * FROM artwork_palettes WHERE artworkPath = :path")
    suspend fun get(path: String): ArtworkPaletteEntity?

    @Query("SELECT * FROM artwork_palettes WHERE artworkPath = :path")
    fun observe(path: String): kotlinx.coroutines.flow.Flow<ArtworkPaletteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(palette: ArtworkPaletteEntity)
}
