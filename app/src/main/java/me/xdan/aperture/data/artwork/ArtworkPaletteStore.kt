package me.xdan.aperture.data.artwork

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import dagger.hilt.android.qualifiers.ApplicationContext
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
import me.xdan.aperture.ui.artwork.extractArtworkAccent
import javax.inject.Inject
import javax.inject.Singleton

internal const val PaletteOnlyRequest = "aperture.paletteOnly"

private val TmdbArtworkUrl = Regex("^https://image\\.tmdb\\.org/t/p/(?:w[0-9]+|original)(/[^?#]+)(?:[?#].*)?$")

internal fun artworkPathFromUrl(url: String): String? = TmdbArtworkUrl.matchEntire(url)?.groupValues?.get(1)

@Singleton
class ArtworkPaletteStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ArtworkPaletteDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val extractionSlots = Semaphore(2)
    private val inFlight = mutableMapOf<String, Deferred<Int?>>()

    fun observeSeed(path: String): kotlinx.coroutines.flow.Flow<Int?> = dao.observe(path).map {
        it?.takeIf { palette -> palette.extractionVersion == ArtworkExtractionVersion }?.seedArgb
    }

    /** Disk-backed seeds survive restarts; caller cancellation does not discard useful precomputation. */
    suspend fun seed(path: String): Int? {
        dao.get(path)?.takeIf { it.extractionVersion == ArtworkExtractionVersion }?.let { return it.seedArgb }
        val pending = synchronized(inFlight) { inFlight[path] }
        pending?.await()?.let { return it }
        val url = backdropImageUrl(path, backdropImageSpec(context, BackdropImageUsage.AMBIENT))
        return compute(path, url, context.imageLoader, null, true)
    }

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
                            val bitmap = (loaded?.drawable as? BitmapDrawable)?.bitmap
                            val seed = if (bitmap != null) {
                                extractArtworkAccent(bitmap)
                            } else {
                                val result = loader.execute(
                                    ImageRequest.Builder(context)
                                        .data(url)
                                        .size(ArtworkSampleSize, ArtworkSampleSize)
                                        .precision(Precision.EXACT)
                                        .allowHardware(false)
                                        .setParameter(PaletteOnlyRequest, true, null)
                                        .memoryCachePolicy(CachePolicy.DISABLED)
                                        // Non-bitmap drawables can only use a cached decode in the
                                        // background. Cinematic prefetch may fetch a missing image.
                                        .networkCachePolicy(if (allowNetwork) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                        .build()
                                )
                                result.drawable?.toBitmap(ArtworkSampleSize, ArtworkSampleSize)
                                    ?.let(::extractArtworkAccent)
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
