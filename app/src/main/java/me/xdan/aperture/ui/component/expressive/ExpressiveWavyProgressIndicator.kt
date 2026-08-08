package me.xdan.aperture.ui.component.expressive

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.xdan.aperture.ui.theme.ApertureTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * A 1:1 high-fidelity Material 3 Expressive wavy progress indicator.
 *
 * Matches official specifications:
 * - Wavelength: 40dp
 * - Amplitude: 3dp
 * - Speed: 40dp/s
 * - Indeterminate Motion: Dual-segment staggered animation with official M3 keyframes.
 */
@Composable
fun ExpressiveWavyProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null, // null for indeterminate
    color: Color = ApertureTheme.colorScheme.primary,
    trackColor: Color = ApertureTheme.colorScheme.surfaceContainerHigh,
    strokeWidth: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavyProgress")
    
    // 1. Wave Scrolling (Linear phase shift at 40dp/s)
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // 2. Official Indeterminate Head/Tail Keyframes (1800ms cycle)
    val headEasing = CubicBezierEasing(0.2f, 0.0f, 0.8f, 1.0f)
    val tailEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    val firstLineHead by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1800
                0f at 0 using headEasing
                1f at 750
                1f at 1800
            }
        ),
        label = "line1Head"
    )
    val firstLineTail by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1800
                0f at 0
                0f at 333 using tailEasing
                1f at 1183
                1f at 1800
            }
        ),
        label = "line1Tail"
    )
    val secondLineHead by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1800
                0f at 0
                0f at 1000 using headEasing
                1f at 1567
                1f at 1800
            }
        ),
        label = "line2Head"
    )
    val secondLineTail by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1800
                0f at 0
                0f at 1267 using tailEasing
                1f at 1800
            }
        ),
        label = "line2Tail"
    )

    // 3. Determinate Progress Animation
    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessVeryLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "wavyDeterminateProgress"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        val widthPx = size.width
        val centerY = size.height / 2f
        val wavelengthPx = 40.dp.toPx()
        val amplitudePx = 3.dp.toPx()
        val strokeWidthPx = strokeWidth.toPx()

        // Helper to generate the path for a given range
        fun generateWavePath(startX: Float, endX: Float): Path = Path().apply {
            if (endX <= startX) return@apply
            
            var first = true
            var x = startX
            val step = 2f
            while (x <= endX) {
                val normalizedX = x / wavelengthPx
                val y = centerY + sin((normalizedX - phaseShift) * 2 * PI).toFloat() * amplitudePx
                if (first) {
                    moveTo(x, y)
                    first = false
                } else {
                    lineTo(x, y)
                }
                x += step
            }
            // Ensure exact termination
            val finalY = centerY + sin((endX / wavelengthPx - phaseShift) * 2 * PI).toFloat() * amplitudePx
            lineTo(endX, finalY)
        }

        // A. Draw the full wavy background track
        drawPath(
            path = generateWavePath(0f, widthPx),
            color = trackColor,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )

        // B. Draw the active segments
        if (progress == null) {
            // Indeterminate: Draw both line segments
            val l1Start = firstLineTail * widthPx
            val l1End = firstLineHead * widthPx
            if (l1End > l1Start) {
                drawPath(
                    path = generateWavePath(l1Start, l1End),
                    color = color,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }

            val l2Start = secondLineTail * widthPx
            val l2End = secondLineHead * widthPx
            if (l2End > l2Start) {
                drawPath(
                    path = generateWavePath(l2Start, l2End),
                    color = color,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }
        } else {
            // Determinate: grows from left to right
            val endX = widthPx * animatedProgress
            if (endX > 0f) {
                drawPath(
                    path = generateWavePath(0f, endX),
                    color = color,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }
        }
    }
}
