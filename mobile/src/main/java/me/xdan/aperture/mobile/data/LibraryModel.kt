package me.xdan.aperture.mobile.data

import me.xdan.aperture.companion.protocol.CollectionKind
import me.xdan.aperture.companion.protocol.CompanionCollection
import me.xdan.aperture.companion.protocol.CompanionMedia
import me.xdan.aperture.companion.protocol.CompanionMediaType

/** episodes sharing a show title, mirroring the TV's grouped show layout. */
data class ShowGroup(val title: String, val episodes: List<CompanionMedia>) {
    val key: String get() = "show:$title"
    val representative: CompanionMedia get() = episodes.first()
    val posterPath: String? get() = episodes.firstNotNullOfOrNull { it.posterPath }
    val backdropPath: String? get() = episodes.firstNotNullOfOrNull { it.backdropPath }
    val overview: String? get() = episodes.firstNotNullOfOrNull { it.overview?.takeIf(String::isNotBlank) }
    val year: Int? get() = episodes.mapNotNull { it.year }.minOrNull()
    val seasons: Map<Int, List<CompanionMedia>> get() = episodes.groupBy { it.seasonNumber ?: 0 }.toSortedMap()
    val isFavorite: Boolean get() = episodes.any { it.isFavorite }

    /** the episode Play should start: the one in progress, else the one after the last finished. */
    val nextUp: CompanionMedia
        get() {
            val inProgress = episodes.filter { it.isInProgress }.maxByOrNull { it.lastWatchedAt ?: 0 }
            if (inProgress != null) return inProgress
            val lastFinished = episodes.withIndex().filter { it.value.completed }.maxByOrNull { it.value.lastWatchedAt ?: 0 }
            return lastFinished?.let { episodes.getOrNull(it.index + 1) } ?: episodes.first()
        }
}

/** either a movie or a grouped show, as shown on browse rows. */
sealed interface LibraryEntry {
    val key: String
    val title: String
    val posterPath: String?

    data class Movie(val media: CompanionMedia) : LibraryEntry {
        override val key get() = "media:${media.id}"
        override val title get() = media.title
        override val posterPath get() = media.posterPath
    }

    data class Show(val group: ShowGroup) : LibraryEntry {
        override val key get() = group.key
        override val title get() = group.title
        override val posterPath get() = group.posterPath
    }
}

val CompanionMedia.isInProgress: Boolean
    get() {
        val position = positionMs ?: return false
        val duration = durationMs ?: return position > 0 && !completed
        return !completed && position > duration * 0.02 && position < duration * 0.95
    }

val CompanionMedia.progressFraction: Float
    get() {
        val duration = durationMs?.takeIf { it > 0 } ?: return 0f
        return ((positionMs ?: 0).toFloat() / duration).coerceIn(0f, 1f)
    }

val CompanionMedia.episodeLabel: String?
    get() = if (type == CompanionMediaType.EPISODE && seasonNumber != null && episodeNumber != null) {
        "S$seasonNumber · E$episodeNumber"
    } else null

data class Library(
    val items: List<CompanionMedia>,
    /** The TV's library entries in its sidebar order; empty from older TVs. */
    private val tvCollections: List<CompanionCollection> = emptyList()
) {
    /** The TV's entries, or Movies and TV shows for a TV that predates collections. */
    val collections: List<CompanionCollection> = tvCollections.ifEmpty {
        listOf(
            CompanionCollection(FALLBACK_MOVIES, "Movies", CollectionKind.MOVIES, "movie"),
            CompanionCollection(FALLBACK_SHOWS, "TV shows", CollectionKind.SHOWS, "tv")
        )
    }

    private val discoverKeys = collections.filter { it.kind == CollectionKind.DISCOVER }.map { it.key }.toSet()

    fun collectionOf(media: CompanionMedia): String =
        media.collection ?: if (media.type == CompanionMediaType.EPISODE) FALLBACK_SHOWS else FALLBACK_MOVIES

    /** A requestable title: the phone offers Download rather than Play. */
    fun isDiscover(media: CompanionMedia): Boolean = collectionOf(media) in discoverKeys

    /** Everything but Discover's placeholders: what Home, Movies and TV shows draw from. */
    private val watchable = items.filterNot(::isDiscover)

    val hasDiscover: Boolean get() = discoverKeys.isNotEmpty()

    /** Discover's titles, newest first. */
    val discover: List<LibraryEntry> by lazy {
        items.filter(::isDiscover).sortedByDescending { it.dateAdded }.map { LibraryEntry.Movie(it) }
    }

    /** A collection's titles: films as themselves, episodes grouped into their shows. */
    fun entriesIn(key: String): List<LibraryEntry> =
        items.filter { collectionOf(it) == key }.map(::entryFor).distinctBy { it.key }.sortedBy { it.title.lowercase() }

    fun count(key: String): Int = entriesIn(key).size

    val movies: List<CompanionMedia> = watchable.filter { it.type != CompanionMediaType.EPISODE }
        .sortedBy { it.title.lowercase() }

    val shows: List<ShowGroup> = watchable.filter { it.type == CompanionMediaType.EPISODE }
        .groupBy { it.title }
        .map { (title, episodes) ->
            ShowGroup(title, episodes.sortedWith(compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }, { it.id })))
        }
        .sortedBy { it.title.lowercase() }

    private val showByTitle = shows.associateBy { it.title }

    fun show(title: String): ShowGroup? = showByTitle[title]

    fun entryFor(media: CompanionMedia): LibraryEntry =
        if (media.type == CompanionMediaType.EPISODE) showByTitle[media.title]?.let(LibraryEntry::Show) ?: LibraryEntry.Movie(media)
        else LibraryEntry.Movie(media)

    /** in-progress titles, one card per show, most recent first. */
    val continueWatching: List<CompanionMedia> = watchable.filter { it.isInProgress }
        .sortedByDescending { it.lastWatchedAt ?: 0 }
        .distinctBy { if (it.type == CompanionMediaType.EPISODE) "show:${it.title}" else "media:${it.id}" }
        .take(20)

    val recentlyAdded: List<LibraryEntry> = watchable.sortedByDescending { it.dateAdded }
        .map(::entryFor)
        .distinctBy { it.key }
        .take(20)

    val favourites: List<LibraryEntry> = watchable.filter { it.isFavorite }
        .map(::entryFor)
        .distinctBy { it.key }

    fun search(query: String): List<LibraryEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return items.filter {
            it.title.lowercase().contains(q) || it.episodeTitle?.lowercase()?.contains(q) == true
        }.map(::entryFor).distinctBy { it.key }
    }
}

/** Collection keys for a TV that sends none; the same as the TV's own Movies and TV shows. */
const val FALLBACK_MOVIES = "movies"
const val FALLBACK_SHOWS = "shows"
