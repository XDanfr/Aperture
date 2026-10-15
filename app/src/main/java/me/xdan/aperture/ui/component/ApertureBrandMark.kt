package me.xdan.aperture.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.xdan.aperture.ui.theme.ApertureTheme

/** The approved opaque mark. The play symbol stays still while the shutter turns. */
@Composable
internal fun ApertureBrandMark(
    modifier: Modifier = Modifier,
    spinBlades: Boolean = false,
    spinKey: Any? = null,
    palette: ApertureBrandPalette = currentApertureBrandPalette()
) {
    val motion = ApertureTheme.motion
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinBlades, spinKey) {
        if (spinBlades) {
            rotation.snapTo(0f)
            rotation.animateBrandTurn(motion)
        } else {
            // 0° and 360° are visually identical; reset to prime the next spin.
            rotation.snapTo(0f)
        }
    }
    ApertureBrandArtwork(modifier = modifier, palette = palette) {
        ApertureBrandPose(rotation = rotation.value)
    }
}
