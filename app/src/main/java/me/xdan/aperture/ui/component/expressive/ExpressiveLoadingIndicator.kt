package me.xdan.aperture.ui.component.expressive

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import me.xdan.aperture.ui.theme.ApertureTheme

/**
 * A high-fidelity Material 3 Expressive Loading Indicator.
 *
 * Continuously cycles through and morphs between a sequence of distinct
 * multi-vertex abstract shapes using the [RoundedPolygon] and [Morph] APIs.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = ApertureTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressiveLoading")

    // The transition progress cycles from 0 to 4 (for 4 shape transitions)
    val transitionProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morphProgress"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val shapes = remember {
        listOf(
            // 1. Pill
            RoundedPolygon(
                numVertices = 4,
                radius = 1f,
                centerX = 0f,
                centerY = 0f,
                rounding = CornerRounding(radius = 0.5f, smoothing = 0.5f)
            ),
            // 2. Cookie
            RoundedPolygon(
                numVertices = 6,
                radius = 1f,
                centerX = 0f,
                centerY = 0f,
                rounding = CornerRounding(radius = 0.8f, smoothing = 0.8f)
            ),
            // 3. Soft Burst
            RoundedPolygon.star(
                numVerticesPerRadius = 8,
                radius = 1f,
                innerRadius = 0.5f,
                centerX = 0f,
                centerY = 0f,
                rounding = CornerRounding(radius = 0.4f, smoothing = 0.4f)
            ),
            // 4. Expressive Polygon
            RoundedPolygon(
                numVertices = 5,
                radius = 1f,
                centerX = 0f,
                centerY = 0f,
                rounding = CornerRounding(radius = 0.4f, smoothing = 0.4f)
            )
        )
    }

    val morphs = remember(shapes) {
        listOf(
            Morph(shapes[0], shapes[1]),
            Morph(shapes[1], shapes[2]),
            Morph(shapes[2], shapes[3]),
            Morph(shapes[3], shapes[0])
        )
    }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation
            }
    ) {
        val currentMorphIndex = transitionProgress.toInt().coerceIn(0, 3)
        val currentMorphProgress = transitionProgress % 1f
        val morph = morphs[currentMorphIndex]
        
        // Target a native path
        val androidPath = morph.toPath(currentMorphProgress)
        val composePath = androidPath.asComposePath()
        
        // Standardize normalization and centering
        val scaleFactor = this.size.minDimension / 2f
        
        translate(left = this.size.width / 2f, top = this.size.height / 2f) {
            scale(scaleFactor) {
                drawPath(path = composePath, color = color)
            }
        }
    }
}
