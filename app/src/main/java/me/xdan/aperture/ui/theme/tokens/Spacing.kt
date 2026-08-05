package me.xdan.aperture.ui.theme.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic spacing tokens for Aperture.
 *
 * These tokens define the layout grid and component spacing, ensuring
 * a consistent rhythm across the TV interface.
 */
data class ApertureSpacing(
    /** No spacing. */
    val none: Dp = 0.dp,
    /** Extra small spacing (4dp). Used for tight grouping. */
    val extraSmall: Dp = 4.dp,
    /** Small spacing (8dp). Standard gap between related elements. */
    val small: Dp = 8.dp,
    /** Medium spacing (16dp). Standard gap between components. */
    val medium: Dp = 16.dp,
    /** Large spacing (24dp). Used for section separation. */
    val large: Dp = 24.dp,
    /** Extra large spacing (32dp). Used for major layout gaps. */
    val extraLarge: Dp = 32.dp,
    /** Huge spacing (48dp). Used for immersive margins. */
    val huge: Dp = 48.dp
)
