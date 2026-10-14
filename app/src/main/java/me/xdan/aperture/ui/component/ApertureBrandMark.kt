package me.xdan.aperture.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var targetRotation by remember { mutableFloatStateOf(0f) }
    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = motion.brandSpatial(),
        label = "Aperture shutter rotation"
    )
    LaunchedEffect(spinBlades, spinKey) {
        if (spinBlades) targetRotation += 360f
        // Closing lets this turn settle. Reopening retargets the same spring,
        // preserving velocity instead of snapping the blades back to zero.
    }
    ApertureBrandArtwork(modifier = modifier, palette = palette) {
        ApertureBrandPose(rotation = rotation)
    }
}
