package me.xdan.aperture.ui.component

import androidx.compose.ui.graphics.Color
import me.xdan.aperture.ui.theme.ApertureThemeOption
import me.xdan.aperture.ui.theme.ApertureThemeOptions
import me.xdan.aperture.ui.theme.InversePrimary
import me.xdan.aperture.ui.theme.OnPrimary
import me.xdan.aperture.ui.theme.Primary
import me.xdan.aperture.ui.theme.apertureDarkColorScheme
import me.xdan.aperture.ui.theme.inversePrimaryForAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.pow

class ApertureBrandThemeTest {
    @Test
    fun everyColouredThemeUsesItsOwnMaterialRoles() {
        ApertureThemeOptions.filter { it.id != "classic" }.forEach { option ->
            val scheme = scheme(option)
            val palette = apertureBrandPalette(scheme, isMaterialTv = false)
            assertEquals(option.label, scheme.inversePrimary, palette.blade)
            assertEquals(option.label, scheme.onPrimary, palette.overlap)
            assertEquals(option.label, scheme.primary, palette.play)
            if (option.id != "purple") {
                // Regression: darkColorScheme used to silently supply the purple default here.
                assertNotEquals(option.label, InversePrimary, palette.blade)
                assertEquals(option.label, 40.0, tone(palette.blade), 0.3)
            }
        }
    }

    @Test
    fun materialTvKeepsTheApprovedPurplePalette() {
        val tv = ApertureThemeOptions.single { it.id == "classic" }
        val scheme = scheme(tv)
        val palette = apertureBrandPalette(scheme, isMaterialTv = true)
        assertEquals(Color.White, scheme.primary)
        assertEquals(InversePrimary, palette.blade)
        assertEquals(OnPrimary, palette.overlap)
        assertEquals(Primary, palette.play)
    }

    @Test
    fun artworkChangesUpdateBothTheBladeAndPlayColours() {
        val dynamic = ApertureThemeOptions.single { it.id == "dynamic" }
        val red = scheme(dynamic.copy(primary = Color(0xFFFF7474)))
        val cyan = scheme(dynamic.copy(primary = Color(0xFF72D4EF)))
        val first = apertureBrandPalette(red, isMaterialTv = false)
        val next = apertureBrandPalette(cyan, isMaterialTv = false)
        assertNotEquals(first.blade, next.blade)
        assertNotEquals(first.play, next.play)
        assertEquals(cyan.inversePrimary, next.blade)
        assertEquals(cyan.onPrimary, next.overlap)
        assertEquals(cyan.primary, next.play)
    }

    @Test
    fun cinematicArtworkSuppliesACompleteReadableTonalPalette() {
        val red = artworkApertureBrandPalette(Color(0xFFDF7468))
        val cyan = artworkApertureBrandPalette(Color(0xFF54B8D2))
        assertNotEquals(red.blade, cyan.blade)
        assertNotEquals(red.overlap, cyan.overlap)
        assertNotEquals(red.play, cyan.play)
        listOf(red, cyan).forEach { palette ->
            assertEquals(40.0, tone(palette.blade), 0.3)
            assertEquals(20.0, tone(palette.overlap), 0.3)
            assertEquals(80.0, tone(palette.play), 0.3)
        }
    }

    private fun scheme(option: ApertureThemeOption) = apertureDarkColorScheme(
        themeId = option.id,
        primary = option.primary,
        inversePrimary = inversePrimaryForAccent(option.primary),
        secondary = option.secondary,
        tertiary = option.tertiary
    )

    /** Independent L* calculation verifies that generated inverse colours use Material tone 40. */
    private fun tone(color: Color): Double {
        fun linear(value: Float): Double = if (value <= 0.04045f) value / 12.92
            else ((value + 0.055) / 1.055).pow(2.4)
        val y = 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
        return if (y <= 216.0 / 24389.0) y * 24389.0 / 27.0 else 116.0 * y.pow(1.0 / 3.0) - 16.0
    }
}
