package me.xdan.aperture.ui.theme.tokens

import androidx.compose.ui.graphics.Color

/**
 * Semantic color roles for Aperture.
 *
 * These roles provide a level of abstraction over Material 3 roles,
 * allowing for specific TV-optimized adjustments without breaking
 * the base Material theme.
 */
data class ApertureColorScheme(
    // Expressive base roles
    val surfaceBright: Color,
    val surfaceDim: Color,
    val surfaceContainer: Color,
    val surfaceContainerLow: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,

    // Semantic roles
    val mediaCardBackground: Color,
    val focusedMediaCardBackground: Color,
    val heroBackground: Color,
    val playbackOverlay: Color,
    val metadataBackground: Color,
    val shelfBackground: Color,
    
    // Core brand
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color
)
