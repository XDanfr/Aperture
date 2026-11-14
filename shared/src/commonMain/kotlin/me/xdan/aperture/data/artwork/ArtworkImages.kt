package me.xdan.aperture.data.artwork

import coil3.Image
import coil3.request.ImageRequest

/** Accent of an image Coil already decoded, when it is bitmap-backed; null otherwise. */
internal expect fun Image.decodedArtworkAccent(): Int?

/** Accent of a palette-only request's result, drawn down to the sample size if needed. */
internal expect fun Image.sampledArtworkAccent(): Int?

/** Ask for a software bitmap whose pixels can be read back (Android hardware bitmaps can't). */
internal expect fun ImageRequest.Builder.readablePixels(): ImageRequest.Builder
