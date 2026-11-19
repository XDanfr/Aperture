package me.xdan.aperture.ui.screen.ambient

import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.domain.catalog.CatalogItem

/**
 * A title ambient mode can show, from this device or a Jellyfin server. [backdrop] and [poster]
 * are a TMDB path (local titles) or a full image URL (Jellyfin); either works as an Artwork
 * Dynamic key.
 */
data class AmbientArtwork(
    val id: String,
    val title: String,
    val year: Int? = null,
    val backdrop: String? = null,
    val poster: String? = null
) {
    /** Which way the cinematic artwork drifts. */
    val driftsRight: Boolean get() = id.hashCode() and 1 == 0
}

internal fun String.isImageUrl() = startsWith("http://") || startsWith("https://")

fun MediaEntity.toAmbientArtwork() = AmbientArtwork(
    id = "local:$id",
    title = title,
    year = year,
    backdrop = backdropPath?.takeIf { it.isNotBlank() },
    poster = posterPath?.takeIf { it.isNotBlank() }
)

fun CatalogItem.toAmbientArtwork() = AmbientArtwork(
    id = "jf:${key.stableId}",
    title = title,
    year = year,
    backdrop = backdrop,
    poster = poster
)
