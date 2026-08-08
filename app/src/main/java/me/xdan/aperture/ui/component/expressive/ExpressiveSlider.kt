package me.xdan.aperture.ui.component.expressive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.xdan.aperture.ui.theme.ApertureTheme
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A TV-centric slider component with Material 3 Expressive aesthetics.
 *
 * Features a high-fidelity "Wavy" track that morphs to flat when unfocused.
 * Optimized for D-pad navigation with fluid physics-based transitions.
 */
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    isFocused: Boolean = false
) {
    val rawProgress = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    
    // Animate progress for fluid D-pad interaction
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = ApertureTheme.motion.focus(),
        label = "sliderProgress"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = ApertureTheme.motion.focus(),
        label = "sliderScale"
    )

    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isFocused) 3f else 0f,
        animationSpec = ApertureTheme.motion.focus(),
        label = "wavyAmplitude"
    )

    val trackColor = ApertureTheme.colorScheme.surfaceContainerHigh
    val activeTrackColor = if (enabled) ApertureTheme.colorScheme.primary else ApertureTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .onKeyEvent { event ->
                if (!isFocused || !enabled) return@onKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    val stepSize = if (steps > 0) {
                        (valueRange.endInclusive - valueRange.start) / (steps + 1)
                    } else {
                        (valueRange.endInclusive - valueRange.start) / 20f
                    }
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onValueChange((value - stepSize).coerceIn(valueRange))
                            true
                        }
                        Key.DirectionRight -> {
                            onValueChange((value + stepSize).coerceIn(valueRange))
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable(enabled)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbSize = 24.dp

        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val wavelength = 40.dp.toPx() // Wider waves for premium feel
            
            // Draw Inactive Track (Full width)
            val fullPath = Path().apply {
                moveTo(0f, centerY)
                var x = 0f
                while (x < widthPx) {
                    val relativeX = x / wavelength
                    val y = centerY + animatedAmplitude.dp.toPx() * sin(relativeX * 2 * PI).toFloat()
                    lineTo(x, y)
                    x += 2f
                }
            }
            drawPath(
                path = fullPath,
                color = trackColor,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw Active Track (Clipped by animated progress)
            val activePath = Path().apply {
                moveTo(0f, centerY)
                var x = 0f
                val activeWidth = widthPx * animatedProgress
                while (x <= activeWidth) {
                    val relativeX = x / wavelength
                    val y = centerY + animatedAmplitude.dp.toPx() * sin(relativeX * 2 * PI).toFloat()
                    lineTo(x, y)
                    x += 2f
                }
            }
            drawPath(
                path = activePath,
                color = activeTrackColor,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Thumb - Locked vertically to center for cleaner look
        Box(
            modifier = Modifier
                .offset {
                    val xPos = (animatedProgress * (widthPx - thumbSize.toPx()))
                    IntOffset(xPos.roundToInt(), 0)
                }
                .size(thumbSize)
                .clip(CircleShape)
                .background(activeTrackColor)
                .then(
                    if (isFocused) {
                        Modifier.border(2.dp, ApertureTheme.colorScheme.onPrimary, CircleShape)
                    } else {
                        Modifier
                    }
                )
        )
    }
}
