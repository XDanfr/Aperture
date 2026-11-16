package me.xdan.aperture.domain.hybrid

import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.RemoteItemEntity

/** A film on this device, on the server, or both. Several copies on either side are versions. */
data class HybridMovie(val tmdbId: String?, val local: List<MediaEntity>, val remote: List<RemoteItemEntity>)

/** A show's episodes on this device, the server's series, and which episodes are the same one. */
data class HybridShow(
    val tmdbId: String?,
    val localEpisodes: List<MediaEntity>,
    val remoteSeries: List<RemoteItemEntity>,
    /** Local episode id to the server episodes with the same season and number. */
    val episodeMatches: Map<Long, List<RemoteItemEntity>>
)

data class HybridIndex(
    val movies: List<HybridMovie>,
    val shows: List<HybridShow>,
    /** The server's address, for building artwork URLs; blank when there's no server. */
    val baseUrl: String = ""
) {
    /** One server film per title the device doesn't have (the first of several versions). */
    val serverOnlyMovies: List<RemoteItemEntity> by lazy { movies.filter { it.local.isEmpty() }.mapNotNull { it.remote.firstOrNull() } }

    /** One server series per show the device has no episodes of. */
    val serverOnlyShows: List<RemoteItemEntity> by lazy { shows.filter { it.localEpisodes.isEmpty() }.mapNotNull { it.remoteSeries.firstOrNull() } }

    /** Local media id to the server items that are the same title, for merging lists. */
    val remoteFor: Map<Long, List<RemoteItemEntity>> by lazy {
        buildMap {
            movies.forEach { m -> if (m.remote.isNotEmpty()) m.local.forEach { put(it.id, m.remote) } }
            shows.forEach { s -> if (s.remoteSeries.isNotEmpty()) s.localEpisodes.forEach { put(it.id, s.remoteSeries) } }
        }
    }

    /** Server item ids that have a local copy, so a merged list can leave them out. */
    val matchedRemoteIds: Set<String> by lazy {
        (movies.filter { it.local.isNotEmpty() }.flatMap { it.remote } +
            shows.filter { it.localEpisodes.isNotEmpty() }.flatMap { it.remoteSeries }).map { it.itemId }.toSet()
    }
}

/**
 * Matches local files with server items by provider id. Only the server's movie and show
 * libraries take part: home video and mixed libraries hold clips that Jellyfin's TMDB lookup
 * sometimes mistakes for films, and a Discover library holds placeholders, so their ids aren't
 * trusted. Local files without a TMDB id stay on their own.
 */
object HybridMatcher {
    fun match(
        local: List<MediaEntity>,
        remote: List<RemoteItemEntity>,
        /** Library id to its collection type. */
        libraryTypes: Map<String, String?>
    ): HybridIndex {
        fun inLibrary(item: RemoteItemEntity, type: String) = libraryTypes[item.libraryId] == type

        val remoteMovies = remote.filter { it.type == "MOVIE" && inLibrary(it, "movies") }
        val remoteSeries = remote.filter { it.type == "SERIES" && inLibrary(it, "tvshows") }
        val remoteEpisodes = remote.filter { it.type == "EPISODE" }.groupBy { it.seriesId }

        // Films: by TMDB id.
        val moviesByTmdb = remoteMovies.filter { !it.tmdbId.isNullOrBlank() }.groupBy { it.tmdbId!! }
        val localMovies = local.filter { it.type == "MOVIE" }
        val localByTmdb = localMovies.filter { it.tmdbId != null }.groupBy { it.tmdbId.toString() }
        val movies = buildList {
            (localByTmdb.keys + moviesByTmdb.keys).forEach { id ->
                add(HybridMovie(id, localByTmdb[id].orEmpty(), moviesByTmdb[id].orEmpty()))
            }
            localMovies.filter { it.tmdbId == null }.forEach { add(HybridMovie(null, listOf(it), emptyList())) }
            remoteMovies.filter { it.tmdbId.isNullOrBlank() }.forEach { add(HybridMovie(null, emptyList(), listOf(it))) }
        }

        // Shows: by the show's TMDB id, then episodes by season and number.
        val seriesByTmdb = remoteSeries.filter { !it.tmdbId.isNullOrBlank() }.groupBy { it.tmdbId!! }
        val localEpisodes = local.filter { it.type == "EPISODE" }
        val localShows = localEpisodes.groupBy { it.tmdbId?.toString() ?: "title:${it.title.lowercase()}" }
        val shows = buildList {
            (localShows.keys + seriesByTmdb.keys).forEach { key ->
                val series = seriesByTmdb[key].orEmpty()
                val episodes = localShows[key].orEmpty()
                val serverEpisodes = series.flatMap { remoteEpisodes[it.itemId].orEmpty() }
                    .groupBy { (it.seasonNumber ?: -1) to (it.episodeNumber ?: -1) }
                val matches = episodes.mapNotNull { episode ->
                    val season = episode.seasonNumber ?: return@mapNotNull null
                    val number = episode.episodeNumber ?: return@mapNotNull null
                    serverEpisodes[season to number]?.let { episode.id to it }
                }.toMap()
                add(HybridShow(key.takeUnless { it.startsWith("title:") }, episodes, series, matches))
            }
            remoteSeries.filter { it.tmdbId.isNullOrBlank() }.forEach { add(HybridShow(null, emptyList(), listOf(it), emptyMap())) }
        }
        return HybridIndex(movies, shows)
    }
}
