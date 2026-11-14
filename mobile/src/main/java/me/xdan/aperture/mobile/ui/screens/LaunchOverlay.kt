package me.xdan.aperture.mobile.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.xdan.aperture.mobile.ui.theme.CompanionMotion
import me.xdan.aperture.ui.component.ApertureBrandArtwork
import me.xdan.aperture.ui.component.ApertureBrandPalette
import me.xdan.aperture.ui.component.ApertureBrandPose
import me.xdan.aperture.ui.component.animateBrandTurn
import kotlin.math.roundToInt

private fun ApertureBrandPalette.lerp(to: ApertureBrandPalette, t: Float) = ApertureBrandPalette(
    blade = androidx.compose.ui.graphics.lerp(blade, to.blade, t),
    overlap = androidx.compose.ui.graphics.lerp(overlap, to.overlap, t),
    play = androidx.compose.ui.graphics.lerp(play, to.play, t),
    background = androidx.compose.ui.graphics.lerp(background, to.background, t)
)

/**
 * plays the TV's launch animation backwards over the app, starting from the mark the
 * system splash showed. it draws the same mark where the splash drew it, removes the
 * splash, then folds the mark away. colours blend to [palette] if the splash differed.
 */
@Composable
fun LaunchOverlay(
    splashPalette: ApertureBrandPalette,
    palette: ApertureBrandPalette,
    splashMark: () -> Rect?,
    onFirstFrame: () -> Unit,
    onFinished: () -> Unit
) {
    val motion = CompanionMotion
    val finished = rememberUpdatedState(onFinished)
    val firstFrame = rememberUpdatedState(onFirstFrame)
    val density = LocalDensity.current

    val seedShape = remember { Animatable(2f) }
    val assembly = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val overlaps = remember { Animatable(1f) }
    val playScale = remember { Animatable(1f) }
    val playAlpha = remember { Animatable(1f) }
    val curtain = remember { Animatable(1f) }
    val blend = remember { Animatable(if (splashPalette == palette) 1f else 0f) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fallbackSize = 160.dp
        val fallback = with(density) {
            val sizePx = fallbackSize.toPx()
            Rect(
                left = (constraints.maxWidth - sizePx) / 2f,
                top = (constraints.maxHeight - sizePx) / 2f,
                right = (constraints.maxWidth + sizePx) / 2f,
                bottom = (constraints.maxHeight + sizePx) / 2f
            )
        }
        val target = remember { androidx.compose.runtime.mutableStateOf<Rect?>(null) }

        LaunchedEffect(Unit) {
            // wait briefly for the system splash to report where it drew the icon.
            target.value = withTimeoutOrNull(600) { snapshotFlow { splashMark() }.first { it != null } } ?: fallback
            // two frames: ours is composed and drawn before the splash disappears.
            withFrameNanos { }
            withFrameNanos { }
            firstFrame.value()
            delay(120)

            // reverse of the TV launch: play symbol and overlaps leave, blades fold back.
            coroutineScope {
                launch { blend.animateTo(1f, motion.brandEffects()) }
                launch {
                    launch { playAlpha.animateTo(0f, motion.brandFastEffects()) }
                    playScale.animateTo(0f, motion.brandFastSpatial())
                }
                launch { overlaps.animateTo(0f, motion.brandEffects()) }
                launch {
                    snapshotFlow { playScale.value }.first { it <= 0.4f }
                    assembly.animateTo(0f, motion.brandSpatial())
                }
            }
            // the seed turns back from cookie to soft square to circle, then dissolves.
            coroutineScope {
                launch { rotation.animateBrandTurn(motion) }
                launch {
                    seedShape.animateTo(1f, motion.brandFastSpatial())
                    seedShape.animateTo(0f, motion.brandFastSpatial())
                }
                launch {
                    snapshotFlow { seedShape.value }.first { it <= 1f }
                    launch { scale.animateTo(0.65f, motion.brandFastSpatial()) }
                    curtain.animateTo(0f, motion.brandEffects())
                }
            }
            finished.value()
        }

        val rect = target.value
        val colours = splashPalette.lerp(palette, blend.value)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = curtain.value }
                .background(colours.background)
        ) {
            if (rect != null) {
                ApertureBrandArtwork(
                    modifier = Modifier
                        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                        .size(with(density) { rect.width.toDp() }),
                    palette = colours
                ) {
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
    }
}
