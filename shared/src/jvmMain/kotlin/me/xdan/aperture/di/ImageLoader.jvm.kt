package me.xdan.aperture.di

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath

internal actual fun newApertureImageLoader(
    context: PlatformContext,
    diskCacheDirectory: ImageCacheDirectory?,
    components: ComponentRegistry.Builder.() -> Unit
): ImageLoader = ImageLoader.Builder(context)
    .components(components)
    .apply {
        if (diskCacheDirectory != null) {
            diskCache {
                DiskCache.Builder()
                    .directory(diskCacheDirectory.path.toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
        }
    }
    .build()
