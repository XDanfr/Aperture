package me.xdan.aperture.di

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.PlatformContext

// Coil's Android defaults (memory cache, cacheDir/image_cache, OkHttp fetcher from the classpath).
internal actual fun newApertureImageLoader(
    context: PlatformContext,
    diskCacheDirectory: ImageCacheDirectory?,
    components: ComponentRegistry.Builder.() -> Unit
): ImageLoader = ImageLoader.Builder(context).components(components).build()
