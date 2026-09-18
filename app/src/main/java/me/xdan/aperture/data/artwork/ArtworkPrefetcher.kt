package me.xdan.aperture.data.artwork

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import me.xdan.aperture.util.BackdropImageUsage
import me.xdan.aperture.util.backdropImageSpec
import me.xdan.aperture.util.backdropImageUrl

@Singleton
class ArtworkPrefetcher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val diskPrefetched = ConcurrentHashMap.newKeySet<String>()

    fun prefetchBackdrop(path: String?) {
        val cleanPath = path?.takeIf(String::isNotBlank) ?: return
        val spec = backdropImageSpec(context, BackdropImageUsage.AMBIENT)
        val url = backdropImageUrl(cleanPath, spec)

        if (!diskPrefetched.add(url)) return

        context.imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(url)
                .size(spec.widthPx, spec.heightPx)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build()
        )
    }

    fun clear() {
        diskPrefetched.clear()
    }
}
