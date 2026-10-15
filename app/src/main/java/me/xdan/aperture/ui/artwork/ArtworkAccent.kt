package me.xdan.aperture.ui.artwork

import android.graphics.Bitmap
import androidx.palette.graphics.Palette

internal const val ArtworkSampleSize = 96
internal const val ArtworkExtractionVersion = 2
internal const val FallbackArtworkSeed = 0xFF6750A4.toInt()

fun extractArtworkAccent(source: Bitmap): Int {
    val scaled = Bitmap.createScaledBitmap(source, ArtworkSampleSize, ArtworkSampleSize, true)
    val bitmap = if (scaled.config == Bitmap.Config.HARDWARE) {
        requireNotNull(scaled.copy(Bitmap.Config.ARGB_8888, false))
    } else scaled
    return try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var count = 0
        var hasColour = false
        for (pixel in pixels) {
            if ((pixel ushr 24) < 180) continue
            pixels[count++] = pixel or 0xFF000000.toInt()
            val r = (pixel shr 16) and 255
            val g = (pixel shr 8) and 255
            val b = pixel and 255
            if (maxOf(r, g, b) - minOf(r, g, b) >= 12) hasColour = true
        }
        if (!hasColour) return FallbackArtworkSeed
        // Exclude transparent pixels before quantization so invisible RGB values cannot win.
        val sample = Bitmap.createBitmap(pixels, 0, count, count, 1, Bitmap.Config.ARGB_8888)
        try {
            // Aperture favours well-represented, saturated swatches over tiny accents or greys.
            Palette.from(sample).maximumColorCount(24).resizeBitmapArea(-1).clearFilters().generate()
                .swatches.filter { it.hsl[1] >= 0.08f && it.hsl[2] in 0.05f..0.95f }
                .maxByOrNull { it.population * (0.5f + it.hsl[1]) }
                ?.rgb ?: FallbackArtworkSeed
        } finally { sample.recycle() }
    } finally {
        if (bitmap !== scaled && bitmap !== source) bitmap.recycle()
        if (scaled !== source) scaled.recycle()
    }
}
