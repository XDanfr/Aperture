package me.xdan.aperture.data.artwork

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import java.util.concurrent.ConcurrentHashMap
import me.xdan.aperture.util.BackdropImageUsage
import me.xdan.aperture.util.ScreenMetrics
import me.xdan.aperture.util.backdropImageSpec
import me.xdan.aperture.util.backdropImageUrl

class ArtworkPrefetcher(
    private val context: PlatformContext,
    private val screen: ScreenMetrics,
    private val imageLoader: () -> ImageLoader
) {
    private val diskPrefetched = ConcurrentHashMap.newKeySet<String>()
    private val memoryWarmed = ConcurrentHashMap.newKeySet<String>()

    fun prefetchBackdrop(path: String?, keepInMemory: Boolean = false) {
        val cleanPath = path?.takeIf(String::isNotBlank) ?: return
        val spec = backdropImageSpec(screen, BackdropImageUsage.HOME)
        val url = backdropImageUrl(cleanPath, spec)
        val alreadyQueued = if (keepInMemory) memoryWarmed.add(url) else diskPrefetched.add(url)

        if (!alreadyQueued) return

        imageLoader().enqueue(
            ImageRequest.Builder(context)
                .data(url)
                .size(spec.widthPx, spec.heightPx)
                .memoryCachePolicy(
                    if (keepInMemory) CachePolicy.ENABLED else CachePolicy.DISABLED
                )
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build()
        )
    }

    fun clear() {
        diskPrefetched.clear()
        memoryWarmed.clear()
    }
}
