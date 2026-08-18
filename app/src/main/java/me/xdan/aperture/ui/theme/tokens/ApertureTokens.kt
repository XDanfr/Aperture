package me.xdan.aperture.ui.theme.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The unified token system for Aperture.
 *
 * Combines color, typography, shape, motion, spacing, and elevation
 * into a single expressive layer.
 */
data class ApertureTokens(
    val colorScheme: ApertureColorScheme,
    val typography: ApertureTypography,
    val shapes: ApertureShapes,
    val motion: ApertureMotion,
    val spacing: ApertureSpacing,
    val elevation: ApertureElevation,
    val brandAccent: Color
)

/**
 * CompositionLocal for Aperture tokens.
 */
val LocalApertureTokens = staticCompositionLocalOf<ApertureTokens> {
    error("No ApertureTokens provided")
}
