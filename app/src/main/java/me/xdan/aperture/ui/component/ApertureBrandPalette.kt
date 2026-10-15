package me.xdan.aperture.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import com.google.android.material.color.MaterialColors
import me.xdan.aperture.ui.theme.ApertureTheme
import me.xdan.aperture.ui.theme.Background
import me.xdan.aperture.ui.theme.InversePrimary
import me.xdan.aperture.ui.theme.OnPrimary
import me.xdan.aperture.ui.theme.Primary
import me.xdan.aperture.ui.theme.inversePrimaryForAccent

internal data class ApertureBrandPalette(
    val blade: Color,
    val overlap: Color,
    val play: Color,
    val background: Color
)

private val AperturePurpleBrandPalette = ApertureBrandPalette(
    blade = InversePrimary,
    overlap = OnPrimary,
    play = Primary,
    background = Background
)

internal fun apertureBrandPalette(scheme: ColorScheme, isMaterialTv: Boolean): ApertureBrandPalette =
    if (isMaterialTv) AperturePurpleBrandPalette else ApertureBrandPalette(
        blade = scheme.inversePrimary,
        overlap = scheme.onPrimary,
        play = scheme.primary,
        background = scheme.background
    )

/** Cinematic artwork owns this palette, independently of the app's selected theme. */
internal fun artworkApertureBrandPalette(accent: Color): ApertureBrandPalette {
    val roles = MaterialColors.getColorRoles(accent.copy(alpha = 1f).toArgb(), false)
    return ApertureBrandPalette(
        blade = inversePrimaryForAccent(accent),
        overlap = Color(roles.onAccent),
        play = Color(roles.accent),
        background = Background
    )
}

/** Resolve on every theme recomposition, including the animated Artwork Dynamic palette. */
@Composable
internal fun currentApertureBrandPalette(): ApertureBrandPalette = apertureBrandPalette(
    scheme = MaterialTheme.colorScheme,
    isMaterialTv = ApertureTheme.isMaterialTv
)
