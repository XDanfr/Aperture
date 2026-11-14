package me.xdan.aperture.data.artwork

import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.getExtra

internal class ArtworkPaletteInterceptor(
    private val palettes: ArtworkPaletteStore,
    private val loader: () -> ImageLoader
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed()
        if (result is SuccessResult && !chain.request.getExtra(PaletteOnlyRequest)) {
            palettes.artworkLoaded(chain.request.data.toString(), result, loader())
        }
        return result
    }
}
