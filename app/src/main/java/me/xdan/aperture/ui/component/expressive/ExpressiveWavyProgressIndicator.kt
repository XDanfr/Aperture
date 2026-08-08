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
 * A high-fidelity Material 3 Expressive wavy progress indicator.
 *
 * Matches official specifications:
 * - Wavelength: 40dp
 * - Amplitude: 3dp
 * - Speed: 40dp/s
 * - Indeterminate Motion: Extended high-velocity phase with 50% segment width.
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

    // 2. Indeterminate Pulse (Extended high-velocity phase)
    // We use a 2000ms cycle with a keyframe that maintains high speed for longer.
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2000
                0f at 0
                0.2f at 400
                0.8f at 1600
                1f at 2000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
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

        // B. Draw the active segment
        if (progress == null) {
            // Indeterminate: segment of fixed width (50%) with a velocity plateau
            val segmentWidth = widthPx * 0.5f
            val totalTravel = widthPx + segmentWidth * 2
            val currentStart = (pulseProgress * totalTravel) - segmentWidth
            
            val clampedStart = currentStart.coerceIn(0f, widthPx)
            val clampedEnd = (currentStart + segmentWidth).coerceIn(0f, widthPx)
            
            if (clampedEnd > clampedStart) {
                drawPath(
                    path = generateWavePath(clampedStart, clampedEnd),
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
