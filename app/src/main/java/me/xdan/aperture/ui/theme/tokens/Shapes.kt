package me.xdan.aperture.ui.theme.tokens

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Semantic shape tokens for Aperture.
 *
 * These tokens define the corner language of Aperture, optimized for
 * Material 3 Expressive principles on a TV screen.
 */
data class ApertureShapes(
    /**
     * Shape used by poster artwork throughout Aperture.
     *
     * Chosen to preserve artwork while matching Material 3 Expressive
     * corner language. Usually a smaller radius to avoid cropping info.
     */
    val poster: CornerBasedShape = RoundedCornerShape(8.dp),

    /**
     * Shape used for large banners and immersive content areas.
     *
     * Features a more pronounced rounding for a premium feel.
     */
    val hero: CornerBasedShape = RoundedCornerShape(24.dp),

    /**
     * Semantic shape for action buttons.
     *
     * Follows the pill-shaped expressive language for clear interactivity.
     */
    val button: CornerBasedShape = RoundedCornerShape(50),

    /**
     * Shape for immersive overlays and modal dialogs.
     */
    val dialog: CornerBasedShape = RoundedCornerShape(28.dp)
)
