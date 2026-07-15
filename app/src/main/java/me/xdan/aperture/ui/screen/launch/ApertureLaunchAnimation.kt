package me.xdan.aperture.ui.screen.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import kotlin.math.pow

private const val AnimationDurationMillis = 2_150

/**
 * Builds the Aperture mark from its individual shutter blades before revealing the play icon.
 * The paths use the same 100 x 100 coordinates as the source SVG.
 */
@Composable
fun ApertureLaunchAnimation(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeline = remember { Animatable(0f) }
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background

    LaunchedEffect(Unit) {
        timeline.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = AnimationDurationMillis,
                easing = LinearEasing
            )
        )
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        ApertureAnimatedMark(
            progress = timeline.value,
            primary = primary,
            modifier = Modifier.size(224.dp)
        )
    }
}

@Composable
private fun ApertureAnimatedMark(
    progress: Float,
    primary: Color,
    modifier: Modifier = Modifier
) {
    val bladePath = remember { apertureBladePath() }
    val playPath = remember { aperturePlayPath() }

    val bladeBrush = remember(primary) {
        Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.32f),
                0.24f to primary.copy(alpha = 0.58f),
                0.72f to primary.copy(alpha = 0.19f),
                1f to Color.White.copy(alpha = 0.07f)
            ),
            start = Offset(35f, 18f),
            end = Offset(76f, 67f)
        )
    }
    val bladeStroke = mix(primary, Color.White, 0.46f)

    // Once the shutter is assembled, pull every blade into the centre and compress it.
    // It then springs past its resting size before settling as the play icon appears.
    val inward = when {
        progress < 0.48f -> 0f
        progress < 0.65f -> lerp(0f, 7.2f, easeInOut(normalise(progress, 0.48f, 0.65f)))
        progress < 0.76f -> 7.2f
        progress < 0.88f -> lerp(7.2f, 0f, easeOutCubic(normalise(progress, 0.76f, 0.88f)))
        else -> 0f
    }
    val markScale = when {
        progress < 0.63f -> 1f
        progress < 0.72f -> lerp(1f, 0.87f, easeInOut(normalise(progress, 0.63f, 0.72f)))
        progress < 0.84f -> lerp(0.87f, 1.055f, easeOutCubic(normalise(progress, 0.72f, 0.84f)))
        progress < 0.94f -> lerp(1.055f, 1f, easeOutCubic(normalise(progress, 0.84f, 0.94f)))
        else -> 1f
    }
    val playProgress = easeOutCubic(normalise(progress, 0.76f, 0.91f))
    val playScale = when {
        progress < 0.76f -> 0f
        progress < 0.87f -> lerp(0f, 1.18f, easeOutCubic(normalise(progress, 0.76f, 0.87f)))
        progress < 0.96f -> lerp(1.18f, 1f, easeOutCubic(normalise(progress, 0.87f, 0.96f)))
        else -> 1f
    }

    Canvas(modifier = modifier) {
        val unit = size.minDimension / 100f

        scale(scale = markScale, pivot = center) {
            scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) {
                repeat(6) { index ->
                    // Leave the first blade on its own briefly, then assemble clockwise.
                    val revealStart = if (index == 0) 0f else 0.105f + (index - 1) * 0.064f
                    val reveal = easeOutCubic(normalise(progress, revealStart, revealStart + 0.145f))
                    val rotation = index * 60f - (1f - reveal) * 34f
                    val bladeScale = lerp(0.46f, 1f, reveal)
                    val revealOffset = lerp(9f, 0f, reveal)

                    rotate(degrees = rotation, pivot = LogoCentre) {
                        translate(top = inward + revealOffset) {
                            scale(scale = bladeScale, pivot = LogoCentre) {
                                // A restrained local shadow gives the SVG's glass elevation without
                                // rasterising the logo or requiring a blur effect on every frame.
                                translate(top = 1.1f) {
                                    drawPath(
                                        path = bladePath,
                                        color = Color.Black,
                                        alpha = 0.24f * reveal
                                    )
                                }
                                drawPath(
                                    path = bladePath,
                                    brush = bladeBrush,
                                    alpha = 0.84f * reveal,
                                    blendMode = BlendMode.Screen
                                )
                                drawPath(
                                    path = bladePath,
                                    color = bladeStroke,
                                    alpha = 0.9f * reveal,
                                    style = Stroke(width = 1.15f, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                }

                if (playScale > 0f) {
                    scale(scale = playScale, pivot = LogoCentre) {
                        drawPath(
                            path = playPath,
                            color = Color.White,
                            alpha = playProgress
                        )
                    }
                }
            }
        }
    }
}

private val LogoCentre = Offset(50f, 50f)

@Suppress("DEPRECATION")
private fun apertureBladePath() = Path().apply {
    moveTo(54f, 20f)
    lineTo(85.2f, 20f)
    quadraticBezierTo(88.5f, 20f, 89.7f, 22.5f)
    quadraticBezierTo(90.5f, 24.3f, 88.1f, 26.7f)
    lineTo(68.7f, 43.1f)
    quadraticBezierTo(65f, 46.8f, 61.3f, 43.1f)
    lineTo(48.1f, 29.9f)
    quadraticBezierTo(44.3f, 26.1f, 47f, 22.3f)
    quadraticBezierTo(49f, 20f, 54f, 20f)
    close()
}

@Suppress("DEPRECATION")
private fun aperturePlayPath() = Path().apply {
    moveTo(45.2f, 38.5f)
    lineTo(62.1f, 47.7f)
    quadraticBezierTo(66.2f, 50f, 62.1f, 52.3f)
    lineTo(45.2f, 61.5f)
    quadraticBezierTo(42f, 63.2f, 41.2f, 59.8f)
    quadraticBezierTo(41f, 59.1f, 41f, 57.6f)
    lineTo(41f, 42.4f)
    quadraticBezierTo(41f, 40.9f, 41.2f, 40.2f)
    quadraticBezierTo(42f, 36.8f, 45.2f, 38.5f)
    close()
}

private fun normalise(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private fun easeOutCubic(value: Float): Float = 1f - (1f - value).pow(3)

private fun easeInOut(value: Float): Float =
    if (value < 0.5f) 4f * value.pow(3) else 1f - (-2f * value + 2f).pow(3) / 2f

private fun mix(first: Color, second: Color, secondFraction: Float): Color = Color(
    red = lerp(first.red, second.red, secondFraction),
    green = lerp(first.green, second.green, secondFraction),
    blue = lerp(first.blue, second.blue, secondFraction),
    alpha = lerp(first.alpha, second.alpha, secondFraction)
)
