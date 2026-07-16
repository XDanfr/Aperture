package me.xdan.aperture.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.tv.material3.MaterialTheme

private const val BladeSpinDurationMillis = 650
private val BladeSpinEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** A glass Aperture mark coloured by the active app theme. */
@Composable
fun ApertureBrandMark(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    spinBlades: Boolean = false,
    spinKey: Any? = null
) {
    val bladeRotation = remember { Animatable(0f) }
    val bladePath = remember { apertureBrandBladePath() }
    val playPath = remember { apertureBrandPlayPath() }
    val bladeBrush = remember(accent) {
        Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.32f),
                0.24f to accent.copy(alpha = 0.58f),
                0.72f to accent.copy(alpha = 0.19f),
                1f to Color.White.copy(alpha = 0.07f)
            ),
            start = Offset(35f, 18f),
            end = Offset(76f, 67f)
        )
    }
    val bladeStroke = mixBrandColor(accent, Color.White, 0.46f)

    LaunchedEffect(spinBlades, spinKey) {
        if (spinBlades) {
            bladeRotation.snapTo(0f)
            bladeRotation.animateTo(
                targetValue = 360f,
                animationSpec = tween(
                    durationMillis = BladeSpinDurationMillis,
                    easing = BladeSpinEasing
                )
            )
        } else {
            // Zero and 360 degrees are visually identical; resetting here primes the next opening.
            bladeRotation.snapTo(0f)
        }
    }

    Canvas(modifier = modifier) {
        val unit = size.minDimension / 100f
        scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) {
            rotate(degrees = bladeRotation.value, pivot = ApertureBrandCentre) {
                repeat(6) { index ->
                    rotate(degrees = index * 60f, pivot = ApertureBrandCentre) {
                        translate(top = 1.1f) {
                            drawPath(
                                path = bladePath,
                                color = Color.Black,
                                alpha = 0.24f
                            )
                        }
                        drawPath(
                            path = bladePath,
                            brush = bladeBrush,
                            alpha = 0.84f,
                            blendMode = BlendMode.Screen
                        )
                        drawPath(
                            path = bladePath,
                            color = bladeStroke,
                            alpha = 0.9f,
                            style = Stroke(width = 1.15f, join = StrokeJoin.Round)
                        )
                    }
                }
            }
            drawPath(path = playPath, color = Color.White)
        }
    }
}

private val ApertureBrandCentre = Offset(50f, 50f)

@Suppress("DEPRECATION")
private fun apertureBrandBladePath() = Path().apply {
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
private fun apertureBrandPlayPath() = Path().apply {
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

private fun mixBrandColor(first: Color, second: Color, secondFraction: Float): Color = Color(
    red = first.red + (second.red - first.red) * secondFraction,
    green = first.green + (second.green - first.green) * secondFraction,
    blue = first.blue + (second.blue - first.blue) * secondFraction,
    alpha = first.alpha + (second.alpha - first.alpha) * secondFraction
)
