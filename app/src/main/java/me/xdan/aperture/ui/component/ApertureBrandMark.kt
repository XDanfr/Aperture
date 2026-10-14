package me.xdan.aperture.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    var spinning by remember { mutableStateOf(false) }
    LaunchedEffect(spinBlades, spinKey) {
        if (spinBlades && !spinning) {
            spinning = true
            scope.launch {
                try {
                    rotation.animateBrandTurn(motion)
                } finally {
                    spinning = false
                }
            }
        }
        // Finish the current turn on close. Repeated opens neither accelerate it
        // nor queue more revolutions. Leaving the composition cancels the scope.
    }
    ApertureBrandArtwork(modifier = modifier, palette = palette) {
        ApertureBrandPose(rotation = rotation.value)
    }
}
