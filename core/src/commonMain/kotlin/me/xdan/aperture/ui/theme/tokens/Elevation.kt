package me.xdan.aperture.ui.theme.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic elevation tokens for Aperture.
 *
 * TV interfaces rely on elevation for visual hierarchy and focus clarity.
 * These tokens provide consistent shadow and Z-space values.
 */
data class ApertureElevation(
    /** Standard card elevation. */
    val card: Dp = 4.dp,
    /** Elevation for a card when it has user focus. */
    val focusedCard: Dp = 12.dp,
    /** Elevation for immersive dialogs and overlays. */
    val dialog: Dp = 24.dp,
    /** Elevation for temporary overlays like tooltips or small menus. */
    val overlay: Dp = 8.dp,
    /** Elevation for high-impact hero banners. */
    val hero: Dp = 16.dp
)
