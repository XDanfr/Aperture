package me.xdan.aperture.ui.theme.tokens

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Semantic shape tokens for Aperture.
 *
 * The shape language is intentionally small: softer artwork corners, larger
 * immersive surfaces, and pill-shaped actions give Aperture expressive variety
 * without making every element look like a different component.
 */
data class ApertureShapes(
    /** Artwork and media-card shape. */
    val poster: CornerBasedShape = RoundedCornerShape(12.dp),

    /** Large immersive surfaces such as hero artwork and featured content. */
    val hero: CornerBasedShape = RoundedCornerShape(32.dp),

    /** Primary action shape. */
    val button: CornerBasedShape = RoundedCornerShape(50),

    /** Modal and dialog surface shape. */
    val dialog: CornerBasedShape = RoundedCornerShape(28.dp),

    /** Menus and contextual surfaces. */
    val menu: CornerBasedShape = RoundedCornerShape(24.dp),

    /** Compact tags and metadata controls. */
    val chip: CornerBasedShape = RoundedCornerShape(50)
)
