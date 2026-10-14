package me.xdan.aperture.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Fixed brand colours from Aperture Purple; app theme changes do not recolour the logo. */
object ApertureBrandColors {
    val blade = Color(0xFF6750A4) // Inverse Primary
    val overlap = Color(0xFF381E72) // On Primary
    val play = Color(0xFFD0BCFF) // Primary
    val background = Color(0xFF111014) // Background
}

data class ApertureBrandPose(
    val seedShape: Float = 0f,
    val assembly: Float = 1f,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val overlapAlpha: Float = 1f,
    val playScale: Float = 1f,
    val playAlpha: Float = 1f
)

/**
 * Shared by launch, drawer and Ambient; the settled paths match artwork/aperture-logo.svg.
 * Actual path intersections keep overlap colours attached while the blades morph.
 * Reading animation state in the draw phase avoids recomposing the surrounding UI.
 */
@Composable
fun ApertureBrandArtwork(
    modifier: Modifier = Modifier,
    pose: () -> ApertureBrandPose = { ApertureBrandPose() }
) {
    val settledBlades = remember { brandBlades(1f, 0f) }
    val settledOverlaps = remember { brandOverlaps(settledBlades) }
    val playPath = remember { brandPlayPath() }
    Canvas(modifier) {
        val state = pose()
        val unit = size.minDimension / 100f
        val blades = if (state.assembly == 1f) settledBlades
            else brandBlades(state.assembly, state.seedShape)
        val overlaps = when {
            state.overlapAlpha <= 0f -> emptyList()
            state.assembly == 1f -> settledOverlaps
            else -> brandOverlaps(blades)
        }
        translate((size.width - 100f * unit) / 2f, (size.height - 100f * unit) / 2f) {
            scale(unit, unit, Offset.Zero) {
                scale(state.scale, pivot = BrandCentre) {
                    rotate(state.rotation, BrandCentre) {
                        if (state.assembly == 0f) {
                            drawPath(brandMorphPath(state.seedShape, 0f, 0f), ApertureBrandColors.blade)
                        } else {
                            blades.forEach { drawPath(it, ApertureBrandColors.blade) }
                            overlaps.forEach {
                                drawPath(it, ApertureBrandColors.overlap, alpha = state.overlapAlpha.coerceIn(0f, 1f))
                            }
                        }
                    }
                    if (state.playAlpha > 0f && state.playScale > 0f) {
                        scale(state.playScale, pivot = BrandCentre) {
                            drawPath(playPath, ApertureBrandColors.play, alpha = state.playAlpha.coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
    }
}

private val BrandCentre = Offset(50f, 50f)

// Eight corresponding quadratic segments enable real circle → soft square → blade morphs.
// Lines use their midpoint as the control point, preserving the approved SVG exactly.
private val BladeStart = Offset(54.3f, 19.8f)
private val BladeSegments = listOf(
    Offset(68.85f, 19.8f) to Offset(83.4f, 19.8f),
    Offset(88.7f, 19.8f) to Offset(89.2f, 23.2f),
    Offset(89.6f, 25.3f) to Offset(86.7f, 27.8f),
    Offset(78.1f, 35.3f) to Offset(69.5f, 42.8f),
    Offset(65f, 47.3f) to Offset(60.5f, 42.8f),
    Offset(54.4f, 36.7f) to Offset(48.3f, 30.6f),
    Offset(43.7f, 26f) to Offset(47.1f, 22.1f),
    Offset(49.2f, 19.8f) to BladeStart
)
private val SquareStart = Offset(42f, 32f)
private val SquareSegments = listOf(
    Offset(50f, 32f) to Offset(58f, 32f),
    Offset(68f, 32f) to Offset(68f, 42f),
    Offset(68f, 50f) to Offset(68f, 58f),
    Offset(68f, 68f) to Offset(58f, 68f),
    Offset(50f, 68f) to Offset(42f, 68f),
    Offset(32f, 68f) to Offset(32f, 58f),
    Offset(32f, 50f) to Offset(32f, 42f),
    Offset(32f, 32f) to SquareStart
)

private fun radialPoint(degrees: Float, radius: Float): Offset {
    val angle = degrees * PI / 180.0
    return BrandCentre + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
}

private val CircleStart = radialPoint(-112.5f, 16f)
private val CircleSegments = List(8) { index ->
    val startAngle = -112.5f + 45f * index
    radialPoint(startAngle + 22.5f, 16f / cos(PI / 8).toFloat()) to radialPoint(startAngle + 45f, 16f)
}

private fun interpolate(a: Offset, b: Offset, progress: Float) = a + (b - a) * progress

private fun rotated(point: Offset, degrees: Float): Offset {
    val radians = degrees * PI / 180.0
    val x = point.x - 50f
    val y = point.y - 50f
    return Offset(
        50f + x * cos(radians).toFloat() - y * sin(radians).toFloat(),
        50f + x * sin(radians).toFloat() + y * cos(radians).toFloat()
    )
}

@Suppress("DEPRECATION")
private fun brandMorphPath(seedShape: Float, assembly: Float, rotation: Float) = Path().apply {
    fun point(circle: Offset, square: Offset, blade: Offset): Offset = rotated(
        interpolate(interpolate(circle, square, seedShape), blade, assembly), rotation
    )
    val start = point(CircleStart, SquareStart, BladeStart)
    moveTo(start.x, start.y)
    repeat(8) { index ->
        val control = point(CircleSegments[index].first, SquareSegments[index].first, BladeSegments[index].first)
        val end = point(CircleSegments[index].second, SquareSegments[index].second, BladeSegments[index].second)
        quadraticBezierTo(control.x, control.y, end.x, end.y)
    }
    close()
}

private fun brandBlades(assembly: Float, seedShape: Float) =
    List(6) { brandMorphPath(seedShape, assembly, it * 60f) }

private fun brandOverlaps(blades: List<Path>) = List(6) { index ->
    Path().apply { op(blades[index], blades[(index + 1) % 6], PathOperation.Intersect) }
}

@Suppress("DEPRECATION")
private fun brandPlayPath() = Path().apply {
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
