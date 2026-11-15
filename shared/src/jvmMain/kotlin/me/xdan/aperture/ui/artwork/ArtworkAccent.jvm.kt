package me.xdan.aperture.ui.artwork

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Desktop twin of the Android extractor: the same 96×96 bilinear sample, alpha cut-off and swatch
 * scoring, over a port of androidx.palette's ColorCutQuantizer (24 colours, no filters).
 */
fun extractArtworkAccent(source: Bitmap): Int {
    val pixels = sample(source)
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
    return ColorCutQuantizer(pixels.copyOf(count), maxColors = 24).swatches
        .filter { it.hsl[1] >= 0.08f && it.hsl[2] in 0.05f..0.95f }
        .maxByOrNull { it.population * (0.5f + it.hsl[1]) }
        ?.rgb ?: FallbackArtworkSeed
}

/** Bilinear downscale to ArtworkSampleSize², read back as unpremultiplied ARGB ints. */
private fun sample(source: Bitmap): IntArray {
    val size = ArtworkSampleSize
    val info = ImageInfo(size, size, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    val scaled = Bitmap().apply { allocPixels(info) }
    try {
        Image.makeFromBitmap(source).use { image ->
            Canvas(scaled).use { canvas ->
                canvas.drawImageRect(
                    image,
                    Rect.makeWH(source.width.toFloat(), source.height.toFloat()),
                    Rect.makeWH(size.toFloat(), size.toFloat()),
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
                    Paint(),
                    true
                )
            }
        }
        val bytes = scaled.readPixels(info, size * 4, 0, 0) ?: return IntArray(0)
        return IntArray(size * size) { i ->
            val o = i * 4
            (bytes[o + 3].toInt() and 255 shl 24) or (bytes[o + 2].toInt() and 255 shl 16) or
                (bytes[o + 1].toInt() and 255 shl 8) or (bytes[o].toInt() and 255)
        }
    } finally {
        scaled.close()
    }
}

private class Swatch(val rgb: Int, val population: Int) {
    val hsl: FloatArray by lazy { rgbToHsl((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255) }
}

/** androidx.core ColorUtils.RGBToHSL. */
private fun rgbToHsl(r: Int, g: Int, b: Int): FloatArray {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f
    val maxC = max(rf, max(gf, bf))
    val minC = min(rf, min(gf, bf))
    val delta = maxC - minC
    var h: Float
    val s: Float
    val l = (maxC + minC) / 2f
    if (maxC == minC) {
        h = 0f
        s = 0f
    } else {
        h = when (maxC) {
            rf -> ((gf - bf) / delta) % 6f
            gf -> ((bf - rf) / delta) + 2f
            else -> ((rf - gf) / delta) + 4f
        }
        s = delta / (1f - abs(2f * l - 1f))
    }
    h = (h * 60f) % 360f
    if (h < 0) h += 360f
    return floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

/** Port of androidx.palette.graphics.ColorCutQuantizer (median cut in RGB555), without filters. */
private class ColorCutQuantizer(pixels: IntArray, maxColors: Int) {
    private val histogram = IntArray(1 shl (WORD_WIDTH * 3))
    private val colors: IntArray
    val swatches: List<Swatch>

    init {
        for (i in pixels.indices) {
            val quantized = quantizeFromRgb888(pixels[i])
            pixels[i] = quantized
            histogram[quantized]++
        }
        colors = histogram.indices.filter { histogram[it] > 0 }.toIntArray()
        swatches = if (colors.size <= maxColors) {
            colors.map { Swatch(approximateToRgb888(it), histogram[it]) }
        } else {
            val queue = PriorityQueue<Vbox>(maxColors) { lhs, rhs -> rhs.volume - lhs.volume }
            queue.offer(Vbox(0, colors.size - 1))
            while (queue.size < maxColors) {
                val box = queue.poll()
                if (box != null && box.canSplit) {
                    queue.offer(box.split())
                    queue.offer(box)
                } else break
            }
            queue.map { it.averageColor() }
        }
    }

    private inner class Vbox(private val lower: Int, private var upper: Int) {
        private var population = 0
        private var minR = 0; private var maxR = 0
        private var minG = 0; private var maxG = 0
        private var minB = 0; private var maxB = 0

        init { fit() }

        val volume get() = (maxR - minR + 1) * (maxG - minG + 1) * (maxB - minB + 1)
        val canSplit get() = 1 + upper - lower > 1

        fun fit() {
            minR = Int.MAX_VALUE; minG = Int.MAX_VALUE; minB = Int.MAX_VALUE
            maxR = Int.MIN_VALUE; maxG = Int.MIN_VALUE; maxB = Int.MIN_VALUE
            var count = 0
            for (i in lower..upper) {
                val color = colors[i]
                count += histogram[color]
                val r = red(color); val g = green(color); val b = blue(color)
                if (r > maxR) maxR = r
                if (r < minR) minR = r
                if (g > maxG) maxG = g
                if (g < minG) minG = g
                if (b > maxB) maxB = b
                if (b < minB) minB = b
            }
            population = count
        }

        fun split(): Vbox {
            val splitPoint = findSplitPoint()
            val newBox = Vbox(splitPoint + 1, upper)
            upper = splitPoint
            fit()
            return newBox
        }

        private fun longestDimension(): Int {
            val r = maxR - minR
            val g = maxG - minG
            val b = maxB - minB
            return when {
                r >= g && r >= b -> COMPONENT_RED
                g >= r && g >= b -> COMPONENT_GREEN
                else -> COMPONENT_BLUE
            }
        }

        private fun findSplitPoint(): Int {
            val dimension = longestDimension()
            modifySignificantOctet(colors, dimension, lower, upper)
            colors.sort(lower, upper + 1)
            modifySignificantOctet(colors, dimension, lower, upper)
            val midPoint = population / 2
            var count = 0
            for (i in lower..upper) {
                count += histogram[colors[i]]
                if (count >= midPoint) return min(upper - 1, i)
            }
            return lower
        }

        fun averageColor(): Swatch {
            var redSum = 0; var greenSum = 0; var blueSum = 0; var total = 0
            for (i in lower..upper) {
                val color = colors[i]
                val pop = histogram[color]
                total += pop
                redSum += pop * red(color)
                greenSum += pop * green(color)
                blueSum += pop * blue(color)
            }
            return Swatch(
                approximateToRgb888(
                    (redSum / total.toFloat()).roundToInt(),
                    (greenSum / total.toFloat()).roundToInt(),
                    (blueSum / total.toFloat()).roundToInt()
                ),
                total
            )
        }
    }

    private companion object {
        const val COMPONENT_RED = -3
        const val COMPONENT_GREEN = -2
        const val COMPONENT_BLUE = -1
        const val WORD_WIDTH = 5
        const val WORD_MASK = (1 shl WORD_WIDTH) - 1

        fun modifySignificantOctet(a: IntArray, dimension: Int, lower: Int, upper: Int) {
            when (dimension) {
                COMPONENT_GREEN -> for (i in lower..upper) {
                    val c = a[i]
                    a[i] = green(c) shl (WORD_WIDTH * 2) or (red(c) shl WORD_WIDTH) or blue(c)
                }
                COMPONENT_BLUE -> for (i in lower..upper) {
                    val c = a[i]
                    a[i] = blue(c) shl (WORD_WIDTH * 2) or (green(c) shl WORD_WIDTH) or red(c)
                }
            }
        }

        fun quantizeFromRgb888(color: Int): Int {
            val r = modifyWordWidth((color shr 16) and 255, 8, WORD_WIDTH)
            val g = modifyWordWidth((color shr 8) and 255, 8, WORD_WIDTH)
            val b = modifyWordWidth(color and 255, 8, WORD_WIDTH)
            return r shl (WORD_WIDTH * 2) or (g shl WORD_WIDTH) or b
        }

        fun approximateToRgb888(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or (modifyWordWidth(r, WORD_WIDTH, 8) shl 16) or
                (modifyWordWidth(g, WORD_WIDTH, 8) shl 8) or modifyWordWidth(b, WORD_WIDTH, 8)

        fun approximateToRgb888(color: Int) = approximateToRgb888(red(color), green(color), blue(color))

        fun red(color: Int) = (color shr (WORD_WIDTH * 2)) and WORD_MASK
        fun green(color: Int) = (color shr WORD_WIDTH) and WORD_MASK
        fun blue(color: Int) = color and WORD_MASK

        fun modifyWordWidth(value: Int, currentWidth: Int, targetWidth: Int): Int {
            val newValue = if (targetWidth > currentWidth) value shl (targetWidth - currentWidth)
            else value shr (currentWidth - targetWidth)
            return newValue and ((1 shl targetWidth) - 1)
        }
    }
}
