package me.xdan.aperture.data.jellyfin

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import me.xdan.aperture.data.artwork.ArtworkPaletteStore
import me.xdan.aperture.data.local.dao.JellyfinDao
import me.xdan.aperture.data.local.entity.JellyfinAccountEntity
import me.xdan.aperture.data.local.entity.RemoteItemEntity
import me.xdan.aperture.domain.catalog.JellyfinImages

/**
 * Downloads a Jellyfin library's artwork ahead of time (posters, backdrops and thumbs into the
 * image disk cache) and works out each title's Artwork Dynamic colour, so screens don't wait on
 * the server and the theme follows a title the first time it's shown. Runs after each sync;
 * titles already done cost a database lookup.
 */
class JellyfinArtworkCache(
    private val context: PlatformContext,
    private val dao: JellyfinDao,
    private val palettes: ArtworkPaletteStore,
    private val imageLoader: () -> ImageLoader
) {
    data class Progress(
        val running: Boolean = false,
        val total: Int = 0,
        val done: Int = 0,
        val currentTitle: String? = null,
        /** Posters seen so far, for the onboarding step to show. */
        val recentPosters: List<String> = emptyList(),
        val finishedAt: Long? = null
    ) {
        val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    }

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()
    private val running = Mutex()

    /** Prepares every title of [account]; a second call while one runs waits for it. */
    suspend fun prepare(account: JellyfinAccountEntity) = running.withLock {
        withContext(Dispatchers.IO) {
            val images = JellyfinImages(account.baseUrl)
            val kinds = dao.libraries(account.id).first().associate { it.id to it.collectionType }
            // Films and shows get everything; home videos just their thumbnails, without colour
            // work; Discover changes too often to fetch ahead, so its tab loads its own artwork.
            val titles = TITLE_TYPES.flatMap { dao.itemsOfType(account.id, it).first() }
                .filter { kinds[it.libraryId] in PREPARED_LIBRARIES } +
                // Episodes only where Home shows them: part-watched (Continue Watching).
                dao.itemsOfType(account.id, "EPISODE").first().filter { it.playbackPositionMs > 0 && !it.played }
            homeVideoThumbnails(images, TITLE_TYPES.flatMap { dao.itemsOfType(account.id, it).first() }
                .filter { kinds[it.libraryId] == "homevideos" })
            _progress.value = Progress(running = true, total = titles.size)
            val slots = Semaphore(PARALLEL_DOWNLOADS)
            val lock = Mutex()
            try {
                coroutineScope {
                    titles.map { entity ->
                        async {
                            slots.withPermit { prepareTitle(images, entity) }
                            lock.withLock {
                                val current = _progress.value
                                val poster = images.item(entity).poster
                                _progress.value = current.copy(
                                    done = current.done + 1,
                                    currentTitle = entity.seriesName ?: entity.name,
                                    recentPosters = if (poster == null) current.recentPosters else (current.recentPosters + poster).takeLast(RECENT_POSTERS)
                                )
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                _progress.value = _progress.value.copy(running = false, currentTitle = null, finishedAt = System.currentTimeMillis())
            }
        }
    }

    /** Home videos: their images only, so the grid fills at once; no colours worked out. */
    private suspend fun homeVideoThumbnails(images: JellyfinImages, videos: List<RemoteItemEntity>) {
        val slots = Semaphore(PARALLEL_DOWNLOADS)
        coroutineScope {
            videos.map { entity ->
                async {
                    slots.withPermit {
                        val item = images.item(entity)
                        listOfNotNull(item.thumb, item.poster).distinct().forEach { download(it) }
                    }
                }
            }.awaitAll()
        }
    }

    /** A removed title's images and colours, from the cache and the palette store. */
    suspend fun forget(account: JellyfinAccountEntity, removed: List<RemoteItemEntity>) {
        if (removed.isEmpty()) return
        val images = JellyfinImages(account.baseUrl)
        palettes.forget(removed.flatMap { entity ->
            val item = images.item(entity)
            listOfNotNull(item.poster, item.backdrop, item.thumb)
        }.toSet())
    }

    private suspend fun prepareTitle(images: JellyfinImages, entity: RemoteItemEntity) {
        val item = images.item(entity)
        val themeArtwork = item.backdrop ?: item.poster ?: return
        // A saved colour means this title's artwork was fetched before.
        if (palettes.hasSeed(themeArtwork)) return
        listOfNotNull(item.poster, item.backdrop, item.thumb).distinct().forEach { download(it) }
        try {
            palettes.seed(themeArtwork)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun download(url: String) {
        try {
            imageLoader().execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private companion object {
        val TITLE_TYPES = listOf("MOVIE", "SERIES", "VIDEO")
        val PREPARED_LIBRARIES = setOf("movies", "tvshows")
        const val PARALLEL_DOWNLOADS = 4
        const val RECENT_POSTERS = 24
    }
}
