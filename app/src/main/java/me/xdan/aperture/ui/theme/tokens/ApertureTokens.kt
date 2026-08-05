package me.xdan.aperture.ui.theme.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.tv.material3.Typography

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
    val elevation: ApertureElevation
)

/**
 * CompositionLocal for Aperture tokens.
 */
val LocalApertureTokens = staticCompositionLocalOf<ApertureTokens> {
    error("No ApertureTokens provided")
}
