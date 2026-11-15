package me.xdan.aperture.data.artwork

import coil3.BitmapImage
import coil3.Image
import coil3.request.ImageRequest
import coil3.toBitmap
import me.xdan.aperture.ui.artwork.extractArtworkAccent

internal actual fun Image.decodedArtworkAccent(): Int? = (this as? BitmapImage)?.bitmap?.let(::extractArtworkAccent)

internal actual fun Image.sampledArtworkAccent(): Int? = extractArtworkAccent(toBitmap())

// Skia bitmaps are always CPU-readable.
internal actual fun ImageRequest.Builder.readablePixels(): ImageRequest.Builder = this
