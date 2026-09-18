package me.xdan.aperture.data.artwork

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import me.xdan.aperture.util.backdropImageSpec
import me.xdan.aperture.util.backdropImageUrl

@Singleton
class ArtworkPrefetcher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val diskPrefetched = mutableSetOf<String>()

    fun prefetchBackdrop(path: String?, keepInMemory: Boolean = false) {
        val cleanPath = path?.takeIf(String::isNotBlank) ?: return
        val spec = backdropImageSpec(context)
        val url = backdropImageUrl(cleanPath, spec)

        if (!keepInMemory && !diskPrefetched.add(url)) return

        context.imageLoader.enqueue(
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
}
