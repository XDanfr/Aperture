package me.xdan.aperture.data.artwork

import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult

internal class ArtworkPaletteInterceptor(
    private val palettes: ArtworkPaletteStore,
    private val loader: () -> ImageLoader
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed(chain.request)
        if (result is SuccessResult && chain.request.parameters.value<Boolean>(PaletteOnlyRequest) != true) {
            palettes.artworkLoaded(chain.request.data.toString(), result, loader())
        }
        return result
    }
}
