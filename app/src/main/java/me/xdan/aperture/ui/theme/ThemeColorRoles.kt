package me.xdan.aperture.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.MaterialColors

/** In a dark Material scheme, Inverse Primary uses tone 40 of the primary tonal palette. */
internal fun inversePrimaryForAccent(accent: Color): Color = Color(
    MaterialColors.getColorRoles(accent.copy(alpha = 1f).toArgb(), true).accent
)

/** Opaque Material tonal roles, shared by browsing and the cinematic logo. */
internal fun dynamicArtworkColorScheme(seed: Color): androidx.tv.material3.ColorScheme {
    val primary = MaterialColors.getColorRoles(seed.toArgb(), false)
    val high = maxOf(seed.red, seed.green, seed.blue)
    val low = minOf(seed.red, seed.green, seed.blue)
    val delta = high - low
    val lightness = (high + low) / 2f
    val saturation = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * lightness - 1f))
    val hue = if (delta == 0f) 0f else (60f * when (high) {
        seed.red -> (seed.green - seed.blue) / delta
        seed.green -> (seed.blue - seed.red) / delta + 2f
        else -> (seed.red - seed.green) / delta + 4f
    } + 360f) % 360f
    // App palette choice: quieter analogous secondary and a hue-shifted tertiary.
    // Material supplies opaque, paired tonal roles for each source colour.
    val secondarySource = Color.hsl(hue, saturation * 0.35f, lightness).toArgb()
    val tertiarySource = Color.hsl((hue + 60f) % 360f, saturation * 0.65f, lightness).toArgb()
    val secondary = MaterialColors.getColorRoles(secondarySource, false)
    val tertiary = MaterialColors.getColorRoles(tertiarySource, false)
    return apertureDarkColorScheme(
        themeId = "dynamic", primary = Color(primary.accent), inversePrimary = inversePrimaryForAccent(seed),
        secondary = Color(secondary.accent), tertiary = Color(tertiary.accent)
    ).copy(
        onPrimary = Color(primary.onAccent), primaryContainer = Color(primary.accentContainer),
        onPrimaryContainer = Color(primary.onAccentContainer),
        onSecondary = Color(secondary.onAccent), secondaryContainer = Color(secondary.accentContainer),
        onSecondaryContainer = Color(secondary.onAccentContainer),
        onTertiary = Color(tertiary.onAccent), tertiaryContainer = Color(tertiary.accentContainer),
        onTertiaryContainer = Color(tertiary.onAccentContainer)
    )
}
