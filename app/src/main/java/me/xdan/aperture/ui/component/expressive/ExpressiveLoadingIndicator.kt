package me.xdan.aperture.ui.component.expressive

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlin.math.max
import kotlin.math.min

private const val MaterialContainerSize = 48f
private const val MaterialActiveIndicatorSize = 38f
private const val MaterialActiveIndicatorScale =
    MaterialActiveIndicatorSize / MaterialContainerSize

/**
 * A 1:1 high-fidelity Material 3 Expressive Loading Indicator adapted for Android TV.
 *
 * Uses the official seven-shape sequence and active-indicator proportions while keeping
 * the component independent from the newer Material 3 Expressive API.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    color: Color = ApertureTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressiveLoading")
    val motion = ApertureTheme.motion

    val cycleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 7f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(motion.expressiveLoadingCycleDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycleProgress"
    )

    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(motion.expressiveLoadingRotationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "baseRotation"
    )

    val shapes = remember {
        listOf(
            RoundedPolygon.star(
                numVerticesPerRadius = 10,
                radius = 1f,
                innerRadius = 0.6f,
                centerX = 0f,
                centerY = 0f,
                rounding = CornerRounding(0.2f)
            ),
            RoundedPolygon(
                numVertices = 9,
                radius = 1f,
                centerX = 0f,
                centerY = 0f,
                rounding = CornerRounding(0.5f)
            ),
            RoundedPolygon(
                numVertices = 5,
                radius = 1f,
                centerX = 0f,
                centerY = 0f,
                rounding = CornerRounding(0.3f)
            ),
            RoundedPolygon(
                numVertices = 4,
                radius = 1f,
                centerX = 0f,
                centerY = 0f,
                rounding = CornerRounding(radius = 0.8f, smoothing = 0.5f)
            ),
            RoundedPolygon.star(
                numVerticesPerRadius = 12,
                radius = 1f,
                innerRadius = 0.7f,
                centerX = 0f,
                centerY = 0f,
                rounding = CornerRounding(0.15f)
            ),
            RoundedPolygon(
                numVertices = 4,
                radius = 1f,
                centerX = 0f,
                centerY = 0f,
                rounding = CornerRounding(0.7f)
            ),
            RoundedPolygon(
                numVertices = 12,
                radius = 1f,
                centerX = 0f,
                centerY = 0f,
                rounding = CornerRounding(0.9f)
            )
        )
    }

    val morphs = remember(shapes) {
        (0 until shapes.size).map { index ->
            Morph(shapes[index], shapes[(index + 1) % shapes.size])
        }
    }

    val shapesScaleFactor = remember(shapes) {
        calculateShapeScaleFactor(shapes) * MaterialActiveIndicatorScale
    }

    val androidPath = remember { android.graphics.Path() }
    val matrix = remember { Matrix() }

    Box(
        modifier = modifier
            .size(size)
            .progressSemantics()
            .drawWithCache {
                onDrawBehind {
                    val segment = cycleProgress.toInt().coerceIn(0, morphs.lastIndex)
                    val segmentProgress = cycleProgress % 1f

                    // Material's emphasized easing gives the morph its snap-and-settle character.
                    val morphProgress =
                        CubicBezierEasing(0.2f, 0f, 0f, 1f).transform(segmentProgress)

                    val rotationWhip = morphProgress * motion.expressiveLoadingMorphRotation
                    val segmentRotation = segment * (360f / shapes.size)
                    val finalRotation = baseRotation + segmentRotation + rotationWhip

                    androidPath.reset()
                    morphs[segment].toPath(morphProgress, androidPath)
                    val composePath = androidPath.asComposePath()

                    val scaleFactor = (this.size.minDimension / 2f) * shapesScaleFactor

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

/**
 * Matches Material 3's scale calculation so rotating shapes stay within their intended bounds.
 */
private fun calculateShapeScaleFactor(shapes: List<RoundedPolygon>): Float {
    var scaleFactor = 1f
    val bounds = FloatArray(4)
    val maxBounds = FloatArray(4)

    shapes.forEach { polygon ->
        polygon.calculateBounds(bounds, approximate = false)
        polygon.calculateMaxBounds(maxBounds)

        val scaleX = (bounds[2] - bounds[0]) / (maxBounds[2] - maxBounds[0])
        val scaleY = (bounds[3] - bounds[1]) / (maxBounds[3] - maxBounds[1])

        scaleFactor = min(scaleFactor, max(scaleX, scaleY))
    }

    return scaleFactor
}
