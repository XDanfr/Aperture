package me.xdan.aperture.ui.screen.ambient

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Stable
internal class AmbientGlassBackdropState(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

internal val LocalAmbientGlassBackdrop = staticCompositionLocalOf<AmbientGlassBackdropState?> { null }

@Composable
internal fun rememberAmbientGlassBackdrop(): AmbientGlassBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { AmbientGlassBackdropState(layer) }
}

/** Record artwork only: glass and foreground content must never feed back into this layer. */
@Composable
internal fun AmbientGlassBackdrop(
    state: AmbientGlassBackdropState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .onGloballyPositioned { state.origin = it.positionInRoot() }
            .drawWithContent {
                state.layer.record { this@drawWithContent.drawContent() }
                drawLayer(state.layer)
            },
        content = content
    )
}

/** Frost only the small card region; the logo, wordmark and clock stay in a separate sharp layer. */
@Composable
internal fun AmbientGlassSurface(
    accent: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val backdrop = LocalAmbientGlassBackdrop.current
    val radius = with(LocalDensity.current) { 32.dp.toPx() }
    val blur = remember(radius) { BlurEffect(radius, radius, TileMode.Clamp) }
    val canBlur = backdrop != null && blur.isSupported()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val base = Color(0xFF111014)
    val tint = Brush.verticalGradient(
        listOf(
            lerp(base, accent, 0.10f).copy(alpha = if (canBlur) 0.46f else 0.78f),
            base.copy(alpha = if (canBlur) 0.62f else 0.86f)
        )
    )
    val rim = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.16f), accent.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f))
    )
    Box(modifier = modifier.clip(shape)) {
        if (canBlur && backdrop != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .onGloballyPositioned { origin = it.positionInRoot() }
                    .graphicsLayer { renderEffect = blur }
                    .drawBehind {
                        val offset = backdrop.origin - origin
                        translate(offset.x, offset.y) { drawLayer(backdrop.layer) }
                    }
            )
        }
        Box(Modifier.matchParentSize().background(tint).border(0.75.dp, rim, shape))
        content()
    }
}
