package me.xdan.aperture.ui.artwork

import android.graphics.Bitmap
import me.xdan.aperture.ui.theme.dynamicArtworkColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ArtworkSeedTest {
    private fun selectArtworkSeed(pixels: IntArray): Int {
        if (pixels.isEmpty()) return FallbackArtworkSeed
        val bitmap = Bitmap.createBitmap(pixels, pixels.size, 1, Bitmap.Config.ARGB_8888)
        return try { extractArtworkAccent(bitmap) } finally { bitmap.recycle() }
    }

    @Test
    fun unrelatedHuesSelectAnExistingDominantHueInsteadOfAnAverage() {
        val red = 0xFFCC3333.toInt()
        val blue = 0xFF3344CC.toInt()
        val bitmap = Bitmap.createBitmap(IntArray(96 * 96) { if (it % 96 < 76) red else blue }, 96, 96, Bitmap.Config.ARGB_8888)
        val selected = try { extractArtworkAccent(bitmap) } finally { bitmap.recycle() }
        fun hue(argb: Int): Float = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }[0]
        fun distance(a: Float, b: Float): Float = kotlin.math.abs(a - b).let { minOf(it, 360f - it) }
        assertTrue("Seed ${selected.toUInt().toString(16)} hue ${hue(selected)} must retain red ${hue(red)} or blue ${hue(blue)}", minOf(distance(hue(selected), hue(red)), distance(hue(selected), hue(blue))) < 5f)
    }

    @Test
    fun transparentPixelsDoNotDrownOutTheArtworkColour() {
        val teal = 0xFF008577.toInt()
        val selected = selectArtworkSeed(IntArray(100) { if (it < 90) 0x00FF0000 else teal })
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(selected, it) }
        assertTrue("Transparent red must not replace the teal hue", hsv[0] in 165f..180f)
    }

    @Test
    fun hardwareArtworkCanBeSampledWithoutRecyclingTheDisplayedBitmap() {
        val software = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF008577.toInt()) }
        val hardware = requireNotNull(software.copy(Bitmap.Config.HARDWARE, false))
        software.recycle()
        try {
            val selected = extractArtworkAccent(hardware)
            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(selected, it) }
            assertTrue(hsv[0] in 165f..180f)
            assertFalse(hardware.isRecycled)
        } finally { hardware.recycle() }
    }

    @Test
    fun EmptyAndMonochromeArtworkUseTheBrandFallback() {
        assertEquals(FallbackArtworkSeed, selectArtworkSeed(intArrayOf()))
        assertEquals(FallbackArtworkSeed, selectArtworkSeed(IntArray(100) { 0xFF777777.toInt() }))
    }

    @Test
    fun dynamicRolesAreOpaqueAndHaveDistinctSecondaryAndTertiaryHues() {
        val scheme = dynamicArtworkColorScheme(Color(0xFF008577))
        assertEquals(1f, scheme.primary.alpha)
        assertEquals(1f, scheme.secondary.alpha)
        assertEquals(1f, scheme.tertiary.alpha)
        assertNotEquals(scheme.primary, scheme.secondary)
        assertNotEquals(scheme.secondary, scheme.tertiary)
    }
}
