package me.xdan.aperture.data.artwork

import coil3.Extras
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.xdan.aperture.data.local.dao.ArtworkPaletteDao
import me.xdan.aperture.data.local.entity.ArtworkPaletteEntity
import me.xdan.aperture.util.BackdropImageUsage
import me.xdan.aperture.util.backdropImageSpec
import me.xdan.aperture.util.backdropImageUrl
import me.xdan.aperture.ui.artwork.ArtworkExtractionVersion
import me.xdan.aperture.ui.artwork.ArtworkSampleSize
import me.xdan.aperture.util.ScreenMetrics

internal val PaletteOnlyRequest = Extras.Key(default = false)

private val TmdbArtworkUrl = Regex("^https://image\\.tmdb\\.org/t/p/(?:w[0-9]+|original)(/[^?#]+)(?:[?#].*)?$")

/** Jellyfin artwork: `<server>/Items/<id>/Images/<type>?tag=…`. The tag changes when the image does. */
private val JellyfinArtworkUrl = Regex("^https?://[^?#]+/Items/[^/]+/Images/[A-Za-z]+\\?(?:[^#]*&)?tag=[^&#]+.*$")

/**
 * The key a seed is saved under: the TMDB path, or for Jellyfin the image URL itself, which is
 * also how screens name Jellyfin artwork.
 */
internal fun artworkPathFromUrl(url: String): String? =
    TmdbArtworkUrl.matchEntire(url)?.groupValues?.get(1) ?: url.takeIf { JellyfinArtworkUrl.matches(it) }

class ArtworkPaletteStore(
    private val context: PlatformContext,
    private val dao: ArtworkPaletteDao,
    private val screen: ScreenMetrics,
    private val imageLoader: () -> ImageLoader
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val extractionSlots = Semaphore(2)
    private val inFlight = mutableMapOf<String, Deferred<Int?>>()

    fun observeSeed(path: String): kotlinx.coroutines.flow.Flow<Int?> = dao.observe(path).map {
        it?.takeIf { palette -> palette.extractionVersion == ArtworkExtractionVersion }?.seedArgb
    }

    /** Whether [path] already has a colour from the current extraction. */
    suspend fun hasSeed(path: String): Boolean =
        dao.get(path)?.extractionVersion == ArtworkExtractionVersion

    /** Disk-backed seeds survive restarts; caller cancellation does not discard useful precomputation. */
    suspend fun seed(path: String): Int? {
        dao.get(path)?.takeIf { it.extractionVersion == ArtworkExtractionVersion }?.let { return it.seedArgb }
        val pending = synchronized(inFlight) { inFlight[path] }
        pending?.await()?.let { return it }
        val url = if (isFullUrl(path)) path else backdropImageUrl(path, backdropImageSpec(screen, BackdropImageUsage.AMBIENT))
        return compute(path, url, imageLoader(), null, true)
    }

    private fun isFullUrl(path: String) = path.startsWith("http://") || path.startsWith("https://")

    fun artworkLoaded(url: String, result: SuccessResult, loader: ImageLoader) {
        val path = artworkPathFromUrl(url) ?: return
        scope.launch { compute(path, url, loader, result, false) }
    }

    private suspend fun compute(
        path: String,
        url: String,
        loader: ImageLoader,
        loaded: SuccessResult?,
        allowNetwork: Boolean
    ): Int? {
        // Share extraction across display sizes and foreground lookups for the same artwork.
        val key = path
        val work = synchronized(inFlight) {
            inFlight.getOrPut(key) {
                scope.async {
                    extractionSlots.withPermit {
                        try {
                            dao.get(path)?.takeIf { it.extractionVersion == ArtworkExtractionVersion }
                                ?.let { return@withPermit it.seedArgb }
                            val decodedSeed = loaded?.image?.decodedArtworkAccent()
                            val seed = if (decodedSeed != null) {
                                decodedSeed
                            } else {
                                val result = loader.execute(
                                    ImageRequest.Builder(context)
                                        .data(url)
                                        .size(ArtworkSampleSize, ArtworkSampleSize)
                                        .precision(Precision.EXACT)
                                        .readablePixels()
                                        .apply { extras[PaletteOnlyRequest] = true }
                                        .memoryCachePolicy(CachePolicy.DISABLED)
                                        // Non-bitmap drawables can only use a cached decode in the
                                        // background. Cinematic prefetch may fetch a missing image.
                                        .networkCachePolicy(if (allowNetwork) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                        .build()
                                )
                                result.image?.sampledArtworkAccent()
                            }
                            seed?.also { dao.put(ArtworkPaletteEntity(path, it, ArtworkExtractionVersion)) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        }
        work.invokeOnCompletion { synchronized(inFlight) { if (inFlight[key] === work) inFlight.remove(key) } }
        return work.await()
    }
}
