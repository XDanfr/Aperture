package me.xdan.aperture.ui.component

import androidx.compose.ui.graphics.Color

/** colours for the shared brand mark; TV and phone resolve them from their own Material schemes. */
data class ApertureBrandPalette(
    val blade: Color,
    val overlap: Color,
    val play: Color,
    val background: Color
)
