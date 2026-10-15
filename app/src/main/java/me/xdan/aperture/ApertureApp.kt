package me.xdan.aperture

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import me.xdan.aperture.data.artwork.ArtworkPaletteInterceptor
import me.xdan.aperture.data.artwork.ArtworkPaletteStore
import javax.inject.Inject

@HiltAndroidApp
class ApertureApp : Application(), ImageLoaderFactory {
    @Inject lateinit var artworkPalettes: ArtworkPaletteStore

    override fun newImageLoader(): ImageLoader {
        lateinit var loader: ImageLoader
        loader = ImageLoader.Builder(this)
            .components { add(ArtworkPaletteInterceptor(artworkPalettes) { loader }) }
            .build()
        return loader
    }
}
