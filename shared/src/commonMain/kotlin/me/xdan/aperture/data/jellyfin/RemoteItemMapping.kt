package me.xdan.aperture.data.jellyfin

import me.xdan.aperture.data.local.entity.RemoteItemEntity
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import java.time.LocalDateTime
import java.time.ZoneOffset

/** What the index keeps from each item. Kept small so large libraries sync in seconds. */
internal object RemoteItemMapping {
    /** Movies, shows, episodes and home videos (a home video library's clips are plain Videos). */
    val indexedKinds = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE, BaseItemKind.VIDEO)

    val fields = listOf(
        ItemFields.PROVIDER_IDS,
        ItemFields.OVERVIEW,
        ItemFields.GENRES,
        ItemFields.DATE_CREATED,
        ItemFields.SORT_NAME,
        ItemFields.MEDIA_SOURCE_COUNT,
        ItemFields.WIDTH,
        ItemFields.HEIGHT,
    )

    val imageTypes = listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.THUMB)

    fun typeOf(kind: BaseItemKind): String? = when (kind) {
        BaseItemKind.MOVIE -> TYPE_MOVIE
        BaseItemKind.SERIES -> TYPE_SERIES
        BaseItemKind.EPISODE -> TYPE_EPISODE
        BaseItemKind.VIDEO -> TYPE_VIDEO
        else -> null
    }

    fun toEntity(item: BaseItemDto, accountId: Long, libraryId: String?, generation: Long): RemoteItemEntity? {
        val type = typeOf(item.type) ?: return null
        val providers = item.providerIds.orEmpty()
        val userData = item.userData
        val ownBackdrop = item.backdropImageTags?.firstOrNull()
        return RemoteItemEntity(
            accountId = accountId,
            itemId = JellyfinIds.of(item.id),
            type = type,
            libraryId = libraryId,
            name = item.name ?: "",
            sortName = item.sortName,
            year = item.productionYear,
            premiereDate = item.premiereDate?.toEpochMs(),
            overview = item.overview,
            tmdbId = providers.provider("Tmdb"),
            imdbId = providers.provider("Imdb"),
            tvdbId = providers.provider("Tvdb"),
            seriesId = item.seriesId?.let(JellyfinIds::of),
            seriesName = item.seriesName,
            seasonId = item.seasonId?.let(JellyfinIds::of),
            seasonNumber = item.parentIndexNumber.takeIf { item.type == BaseItemKind.EPISODE },
            episodeNumber = item.indexNumber.takeIf { item.type == BaseItemKind.EPISODE },
            dateCreated = item.dateCreated?.toEpochMs(),
            communityRating = item.communityRating,
            officialRating = item.officialRating,
            runtimeMs = item.runTimeTicks?.let { it / TICKS_PER_MS },
            genres = item.genres?.takeIf { it.isNotEmpty() }?.joinToString("|"),
            primaryImageTag = item.imageTags?.get(ImageType.PRIMARY),
            backdropImageTag = ownBackdrop ?: item.parentBackdropImageTags?.firstOrNull(),
            backdropItemId = if (ownBackdrop != null) null else item.parentBackdropItemId?.let(JellyfinIds::of),
            thumbImageTag = item.imageTags?.get(ImageType.THUMB) ?: item.parentThumbImageTag,
            seriesPrimaryImageTag = item.seriesPrimaryImageTag,
            width = item.width,
            height = item.height,
            container = item.container,
            mediaSourceCount = item.mediaSourceCount ?: 1,
            played = userData?.played ?: false,
            playbackPositionMs = (userData?.playbackPositionTicks ?: 0) / TICKS_PER_MS,
            playCount = userData?.playCount ?: 0,
            isFavorite = userData?.isFavorite ?: false,
            lastPlayedAt = userData?.lastPlayedDate?.toEpochMs(),
            syncGeneration = generation
        )
    }

    /** Provider keys are matched case-insensitively; servers and plugins disagree on casing. */
    private fun Map<String, String?>.provider(name: String): String? =
        entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.takeIf { it.isNotBlank() }

    /** Jellyfin's dates are UTC. */
    fun LocalDateTime.toEpochMs(): Long = toInstant(ZoneOffset.UTC).toEpochMilli()

    fun epochMsToUtc(ms: Long): LocalDateTime =
        LocalDateTime.ofEpochSecond(ms / 1000, ((ms % 1000) * 1_000_000).toInt(), ZoneOffset.UTC)

    const val TICKS_PER_MS = 10_000L
    const val TYPE_MOVIE = "MOVIE"
    const val TYPE_SERIES = "SERIES"
    const val TYPE_EPISODE = "EPISODE"
    const val TYPE_VIDEO = "VIDEO"
}
