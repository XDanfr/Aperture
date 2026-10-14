package me.xdan.aperture.ui.screen.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.xdan.aperture.ui.component.ApertureBrandArtwork
import me.xdan.aperture.ui.component.currentApertureBrandPalette
import me.xdan.aperture.ui.component.ApertureBrandPose
import me.xdan.aperture.ui.component.animateBrandTurn
import me.xdan.aperture.ui.theme.ApertureTheme

/** Circle → soft square → circle → six blades, followed by a springing play symbol. */
@Composable
fun ApertureLaunchAnimation(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val colors = currentApertureBrandPalette()
    val motion = ApertureTheme.motion
    val finished = rememberUpdatedState(onFinished)
    val seedShape = remember { Animatable(0f) }
    val assembly = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(0.65f) }
    val overlaps = remember { Animatable(0f) }
    val playScale = remember { Animatable(0f) }
    val playAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { rotation.animateBrandTurn(motion) }
            launch {
                coroutineScope {
                    launch { scale.animateTo(1f, motion.brandFastSpatial()) }
                    seedShape.animateTo(1f, motion.brandFastSpatial())
                }
                seedShape.animateTo(0f, motion.brandFastSpatial())
                coroutineScope {
                    launch { assembly.animateTo(1f, motion.brandSlowSpatial()) }
                    launch {
                        snapshotFlow { assembly.value }.first { it >= 0.7f }
                        overlaps.animateTo(1f, motion.brandEffects())
                    }
                    launch {
                        snapshotFlow { assembly.value }.first { it >= 0.9f }
                        coroutineScope {
                            launch { playAlpha.animateTo(1f, motion.brandFastEffects()) }
                            playScale.animateTo(1f, motion.brandFastSpatial())
                        }
                    }
                }
            }
        }
        // Completion follows the springs. System animator duration scaling applies,
        // including finishing immediately when animations are disabled.
        finished.value()
    }
    Box(
        modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        ApertureBrandArtwork(Modifier.size(224.dp)) {
            ApertureBrandPose(
                seedShape = seedShape.value,
                assembly = assembly.value,
                rotation = rotation.value,
                scale = scale.value,
                overlapAlpha = overlaps.value,
                playScale = playScale.value,
                playAlpha = playAlpha.value
            )
        }
    }
}
