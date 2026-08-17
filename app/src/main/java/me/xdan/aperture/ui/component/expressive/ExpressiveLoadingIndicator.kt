package me.xdan.aperture.ui.component.expressive

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import me.xdan.aperture.ui.theme.ApertureTheme

/**
 * A 1:1 high-fidelity Material 3 Expressive Loading Indicator adapted for Android TV.
 *
 * Exact Material shape sequence with Aperture's TV-tuned timing.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = ApertureTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressiveLoading")
    val motion = ApertureTheme.motion

    // 7 shapes across a TV-tuned 5 second cycle.
    val cycleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(motion.expressiveLoadingCycleDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycleProgress"
    )

    // Keep the rotation relationship with the morph cycle at 2:1.
    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(motion.expressiveLoadingRotationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "baseRotation"
    )

    val shapes = remember {
        listOf(
            // Soft Burst
            RoundedPolygon.star(numVerticesPerRadius = 10, radius = 1f, innerRadius = 0.6f, centerX = 0f, centerY = 0f, rounding = CornerRounding(0.2f)),
            // Cookie 9-Sided
            RoundedPolygon(numVertices = 9, radius = 1f, centerX = 0f, centerY = 0f, rounding = CornerRounding(0.5f)),
            // Pentagon
            RoundedPolygon(numVertices = 5, radius = 1f, centerX = 0f, centerY = 0f, rounding = CornerRounding(0.3f)),
            // Pill
            RoundedPolygon(numVertices = 4, radius = 1f, centerX = 0f, centerY = 0f, rounding = CornerRounding(radius = 0.8f, smoothing = 0.5f)),
            // Sunny
            RoundedPolygon.star(numVerticesPerRadius = 12, radius = 1f, innerRadius = 0.7f, centerX = 0f, centerY = 0f, rounding = CornerRounding(0.15f)),
            // Cookie 4-Sided
            RoundedPolygon(numVertices = 4, radius = 1f, centerX = 0f, centerY = 0f, rounding = CornerRounding(0.7f)),
            // Oval
            RoundedPolygon(numVertices = 12, radius = 1f, centerX = 0f, centerY = 0f, rounding = CornerRounding(0.9f))
        )
    }

    val morphs = remember(shapes) {
        (0 until 7).map { i ->
            Morph(shapes[i], shapes[(i + 1) % 7])
        }
    }

    val androidPath = remember { android.graphics.Path() }
    val matrix = remember { Matrix() }

    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                onDrawBehind {
                    val segment = cycleProgress.toInt().coerceIn(0, 6)
                    val segmentProgress = cycleProgress % 1f

                    // Emphasized easing for the snappy morph segments.
                    val morphProgress = CubicBezierEasing(0.2f, 0f, 0f, 1f).transform(segmentProgress)

                    // Non-linear rotation whip: accelerate during the morph.
                    val rotationWhip = morphProgress * motion.expressiveLoadingMorphRotation
                    val finalRotation = baseRotation + (segment * (360f / 7f)) + rotationWhip

                    androidPath.reset()
                    morphs[segment].toPath(morphProgress, androidPath)
                    val composePath = androidPath.asComposePath()

                    // Material's 38dp active size within a 48dp container.
                    val scaleFactor = (this.size.minDimension / 2f) * (38f / 48f)

                    matrix.reset()
                    matrix.translate(this.size.width / 2f, this.size.height / 2f)
                    matrix.rotateZ(finalRotation)
                    matrix.scale(scaleFactor, scaleFactor)

                    composePath.transform(matrix)
                    drawPath(path = composePath, color = color)
                }
            }
    )
}
