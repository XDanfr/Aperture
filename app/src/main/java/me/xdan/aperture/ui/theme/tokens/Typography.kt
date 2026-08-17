package me.xdan.aperture.ui.theme.tokens

import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.Typography

/**
 * Semantic typography tokens for Aperture.
 *
 * The base TV Material typography keeps the complete M3 hierarchy intact while
 * exposing semantic styles for the places where Aperture needs extra emphasis.
 */
data class ApertureTypography(
    val base: Typography = Typography()
) {
    /** Large, brand-forward title used for hero content. */
    val heroTitle: TextStyle = base.displayMedium

    /** Section heading used to separate shelves and major content groups. */
    val sectionTitle: TextStyle = base.titleLarge

    /** Compact title for media, dialogs, and supporting surfaces. */
    val mediaTitle: TextStyle = base.titleMedium

    /** Supporting metadata that should remain quiet beside artwork and titles. */
    val metadata: TextStyle = base.bodySmall

    /** Primary action label used by buttons and other controls. */
    val actionLabel: TextStyle = base.labelLarge

    /** Small contextual label used sparingly above or beside content. */
    val eyebrow: TextStyle = base.labelMedium
}
