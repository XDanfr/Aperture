package me.xdan.aperture.ui.theme.tokens

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.graphics.Color

/**
 * Semantic color roles for Aperture.
 *
 * This class follows the Material 3 implementation pattern, using [mutableStateOf]
 * to allow for efficient, fine-grained recomposition when colors are updated
 * (e.g., during theme animations or dynamic accent changes).
 */
@Stable
class ApertureColorScheme(
    surfaceBright: Color,
    surfaceDim: Color,
    surfaceContainer: Color,
    surfaceContainerLow: Color,
    surfaceContainerHigh: Color,
    surfaceContainerHighest: Color,
    mediaCardBackground: Color,
    focusedMediaCardBackground: Color,
    heroBackground: Color,
    playbackOverlay: Color,
    metadataBackground: Color,
    shelfBackground: Color,
    primary: Color,
    onPrimary: Color,
    secondary: Color,
    background: Color,
    onBackground: Color,
    surface: Color,
    onSurface: Color,
    border: Color,
    borderVariant: Color
) {
    var surfaceBright by mutableStateOf(surfaceBright, structuralEqualityPolicy())
        internal set
    var surfaceDim by mutableStateOf(surfaceDim, structuralEqualityPolicy())
        internal set
    var surfaceContainer by mutableStateOf(surfaceContainer, structuralEqualityPolicy())
        internal set
    var surfaceContainerLow by mutableStateOf(surfaceContainerLow, structuralEqualityPolicy())
        internal set
    var surfaceContainerHigh by mutableStateOf(surfaceContainerHigh, structuralEqualityPolicy())
        internal set
    var surfaceContainerHighest by mutableStateOf(surfaceContainerHighest, structuralEqualityPolicy())
        internal set
    var mediaCardBackground by mutableStateOf(mediaCardBackground, structuralEqualityPolicy())
        internal set
    var focusedMediaCardBackground by mutableStateOf(focusedMediaCardBackground, structuralEqualityPolicy())
        internal set
    var heroBackground by mutableStateOf(heroBackground, structuralEqualityPolicy())
        internal set
    var playbackOverlay by mutableStateOf(playbackOverlay, structuralEqualityPolicy())
        internal set
    var metadataBackground by mutableStateOf(metadataBackground, structuralEqualityPolicy())
        internal set
    var shelfBackground by mutableStateOf(shelfBackground, structuralEqualityPolicy())
        internal set
    var primary by mutableStateOf(primary, structuralEqualityPolicy())
        internal set
    var onPrimary by mutableStateOf(onPrimary, structuralEqualityPolicy())
        internal set
    var secondary by mutableStateOf(secondary, structuralEqualityPolicy())
        internal set
    var background by mutableStateOf(background, structuralEqualityPolicy())
        internal set
    var onBackground by mutableStateOf(onBackground, structuralEqualityPolicy())
        internal set
    var surface by mutableStateOf(surface, structuralEqualityPolicy())
        internal set
    var onSurface by mutableStateOf(onSurface, structuralEqualityPolicy())
        internal set
    var border by mutableStateOf(border, structuralEqualityPolicy())
        internal set
    var borderVariant by mutableStateOf(borderVariant, structuralEqualityPolicy())
        internal set

    /**
     * Updates this color scheme with values from [other].
     */
    fun updateFrom(other: ApertureColorScheme) {
        surfaceBright = other.surfaceBright
        surfaceDim = other.surfaceDim
        surfaceContainer = other.surfaceContainer
        surfaceContainerLow = other.surfaceContainerLow
        surfaceContainerHigh = other.surfaceContainerHigh
        surfaceContainerHighest = other.surfaceContainerHighest
        mediaCardBackground = other.mediaCardBackground
        focusedMediaCardBackground = other.focusedMediaCardBackground
        heroBackground = other.heroBackground
        playbackOverlay = other.playbackOverlay
        metadataBackground = other.metadataBackground
        shelfBackground = other.shelfBackground
        primary = other.primary
        onPrimary = other.onPrimary
        secondary = other.secondary
        background = other.background
        onBackground = other.onBackground
        surface = other.surface
        onSurface = other.onSurface
        border = other.border
        borderVariant = other.borderVariant
    }

    /**
     * Creates a copy of this color scheme.
     */
    fun copy(
        surfaceBright: Color = this.surfaceBright,
        surfaceDim: Color = this.surfaceDim,
        surfaceContainer: Color = this.surfaceContainer,
        surfaceContainerLow: Color = this.surfaceContainerLow,
        surfaceContainerHigh: Color = this.surfaceContainerHigh,
        surfaceContainerHighest: Color = this.surfaceContainerHighest,
        mediaCardBackground: Color = this.mediaCardBackground,
        focusedMediaCardBackground: Color = this.focusedMediaCardBackground,
        heroBackground: Color = this.heroBackground,
        playbackOverlay: Color = this.playbackOverlay,
        metadataBackground: Color = this.metadataBackground,
        shelfBackground: Color = this.shelfBackground,
        primary: Color = this.primary,
        onPrimary: Color = this.onPrimary,
        secondary: Color = this.secondary,
        background: Color = this.background,
        onBackground: Color = this.onBackground,
        surface: Color = this.surface,
        onSurface: Color = this.onSurface,
        border: Color = this.border,
        borderVariant: Color = this.borderVariant
    ): ApertureColorScheme = ApertureColorScheme(
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        mediaCardBackground = mediaCardBackground,
        focusedMediaCardBackground = focusedMediaCardBackground,
        heroBackground = heroBackground,
        playbackOverlay = playbackOverlay,
        metadataBackground = metadataBackground,
        shelfBackground = shelfBackground,
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        border = border,
        borderVariant = borderVariant
    )
}
