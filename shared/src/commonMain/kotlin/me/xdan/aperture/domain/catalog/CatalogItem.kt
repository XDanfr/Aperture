package me.xdan.aperture.domain.catalog

import me.xdan.aperture.data.local.entity.RemoteItemEntity
import me.xdan.aperture.domain.model.MediaKey

/**
 * Something to show in a grid or row, wherever it lives. Artwork is a full URL (Jellyfin) or a
 * TMDB path; [me.xdan.aperture.ui.component.artworkUrl] turns either into something to load.
 */
data class CatalogItem(
    val key: MediaKey,
    val kind: Kind,
    val title: String,
    val year: Int? = null,
    val overview: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val thumb: String? = null,
    val seriesTitle: String? = null,
    /** The episode's own name; [title] is the show's for episodes, so rows read by show. */
    val episodeTitle: String? = null,
    /** The show an episode belongs to, as the server's item id. */
    val seriesId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val runtimeMs: Long? = null,
    val rating: Float? = null,
    val officialRating: String? = null,
    val genres: List<String> = emptyList(),
    /** 0..1 through playback, 0 when not started or finished. */
    val progress: Float = 0f,
    val played: Boolean = false,
    val isFavorite: Boolean = false,
    val dateAdded: Long? = null,
    val libraryId: String? = null
) {
    enum class Kind { MOVIE, SHOW, EPISODE, VIDEO }

    /** "S1 · E2" for episodes. */
    val episodeLabel: String?
        get() = if (kind == Kind.EPISODE && seasonNumber != null && episodeNumber != null) "S$seasonNumber · E$episodeNumber" else null
}

/** Builds image URLs for a Jellyfin server. Image endpoints don't need the access token. */
class JellyfinImages(private val baseUrl: String) {
    fun image(itemId: String, type: String, tag: String?, maxWidth: Int): String? =
        tag?.let { "$baseUrl/Items/$itemId/Images/$type?tag=$it&maxWidth=$maxWidth&quality=90" }

    /** An episode's show poster (its own Primary image is a 16:9 still); a film's own poster. */
    fun titlePoster(entity: RemoteItemEntity): String? =
        if (entity.type == "EPISODE") entity.seriesId?.let { image(it, "Primary", entity.seriesPrimaryImageTag, POSTER_WIDTH) }
        else image(entity.itemId, "Primary", entity.primaryImageTag, POSTER_WIDTH)

    fun item(entity: RemoteItemEntity): CatalogItem {
        val kind = when (entity.type) {
            "SERIES" -> CatalogItem.Kind.SHOW
            "EPISODE" -> CatalogItem.Kind.EPISODE
            "VIDEO" -> CatalogItem.Kind.VIDEO
            else -> CatalogItem.Kind.MOVIE
        }
        val poster = image(entity.itemId, "Primary", entity.primaryImageTag, POSTER_WIDTH)
            ?: entity.seriesId?.let { image(it, "Primary", entity.seriesPrimaryImageTag, POSTER_WIDTH) }
        val backdrop = image(entity.backdropItemId ?: entity.itemId, "Backdrop", entity.backdropImageTag, BACKDROP_WIDTH)
        val runtime = entity.runtimeMs
        val progress = if (!entity.played && runtime != null && runtime > 0 && entity.playbackPositionMs > 0) {
            (entity.playbackPositionMs.toFloat() / runtime).coerceIn(0f, 1f)
        } else 0f
        return CatalogItem(
            key = MediaKey.Jellyfin(entity.accountId, entity.itemId),
            kind = kind,
            title = if (kind == CatalogItem.Kind.EPISODE) entity.seriesName ?: entity.name else entity.name,
            year = entity.year,
            overview = entity.overview,
            poster = poster,
            backdrop = backdrop,
            thumb = image(entity.itemId, if (kind == CatalogItem.Kind.EPISODE) "Primary" else "Thumb",
                if (kind == CatalogItem.Kind.EPISODE) entity.primaryImageTag else entity.thumbImageTag, BACKDROP_WIDTH),
            seriesTitle = entity.seriesName,
            episodeTitle = if (kind == CatalogItem.Kind.EPISODE) entity.name else null,
            seriesId = entity.seriesId,
            seasonNumber = entity.seasonNumber,
            episodeNumber = entity.episodeNumber,
            runtimeMs = runtime,
            rating = entity.communityRating,
            officialRating = entity.officialRating,
            genres = entity.genres?.split('|').orEmpty().filter { it.isNotBlank() },
            progress = progress,
            played = entity.played,
            isFavorite = entity.isFavorite,
            dateAdded = entity.dateCreated,
            libraryId = entity.libraryId
        )
    }

    private companion object {
        const val POSTER_WIDTH = 500
        const val BACKDROP_WIDTH = 1280
    }
}
