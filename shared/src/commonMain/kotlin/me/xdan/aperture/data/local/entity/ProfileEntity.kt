package me.xdan.aperture.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Someone who watches on this device. Profile 1 always exists. */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** An [me.xdan.aperture.ui.theme.ApertureThemeOptions] id for the avatar. */
    val colorId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_ID = 1L
    }
}

/** A profile's My List and hidden state for one media file. Rows only exist once either is set. */
@Entity(
    tableName = "profile_media_state",
    primaryKeys = ["profileId", "mediaId"],
    indices = [androidx.room.Index("mediaId")]
)
data class ProfileMediaStateEntity(
    val profileId: Long,
    val mediaId: Long,
    val isFavorite: Boolean = false,
    val favoriteAddedAt: Long? = null,
    val isHidden: Boolean = false
)
