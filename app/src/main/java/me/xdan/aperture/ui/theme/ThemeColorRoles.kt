package me.xdan.aperture.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.MaterialColors

/** In a dark Material scheme, Inverse Primary uses tone 40 of the primary tonal palette. */
internal fun inversePrimaryForAccent(accent: Color): Color = Color(
    MaterialColors.getColorRoles(accent.copy(alpha = 1f).toArgb(), true).accent
)
