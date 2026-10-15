package me.xdan.aperture.ui.theme.tokens

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The unified token system for Aperture.
 *
 * Combines color, typography, shape, motion, spacing, and elevation
 * into a single expressive layer.
 */
@Stable
class ApertureTokens(
    val colorScheme: ApertureColorScheme,
    val typography: ApertureTypography,
    val shapes: ApertureShapes,
    val motion: ApertureMotion,
    val spacing: ApertureSpacing,
    val elevation: ApertureElevation,
    brandAccent: Color,
    isMaterialTv: Boolean
) {
    var brandAccent by mutableStateOf(brandAccent)
        internal set
    var isMaterialTv by mutableStateOf(isMaterialTv)
        internal set
}

/**
 * CompositionLocal for Aperture tokens.
 */
val LocalApertureTokens = staticCompositionLocalOf<ApertureTokens> {
    error("No ApertureTokens provided")
}
