package me.xdan.aperture.ui.component.expressive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import me.xdan.aperture.ui.theme.ApertureTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A TV-centric Material 3 Expressive slider.
 *
 * Uses the expressive slider geometry of a thick 16.dp track with a thin,
 * tall selector handle that cuts through it. When [neutralValue] is supplied,
 * the active track grows from that value to the current value. Without a
 * neutral value, it grows from the start of the range. Ticks are optional so
 * continuous controls can stay visually clean.
 */
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    isFocused: Boolean = false,
    neutralValue: Float? = null,
    showTicks: Boolean = false,
    tickCount: Int = 21
) {
    require(valueRange.start < valueRange.endInclusive) {
        "valueRange must have a smaller start than endInclusive"
    }
    require(steps >= 0) { "steps must be non-negative" }
    require(tickCount >= 2) { "tickCount must be at least 2" }

    val clampedValue = value.coerceIn(valueRange)
    val totalRange = valueRange.endInclusive - valueRange.start
    val rawProgress = (clampedValue - valueRange.start) / totalRange
    val neutralProgress = neutralValue
        ?.coerceIn(valueRange)
        ?.let { (it - valueRange.start) / totalRange }
        ?: 0f

    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = ApertureTheme.motion.focus(),
        label = "sliderProgress"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = ApertureTheme.motion.focus(),
        label = "sliderScale"
    )
    val animatedSelectorScale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = ApertureTheme.motion.focus(),
        label = "sliderSelectorScale"
    )

    val trackColor = ApertureTheme.colorScheme.surfaceContainerHigh
    val activeTrackColor = if (enabled) {
        ApertureTheme.colorScheme.primary
    } else {
        ApertureTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val inactiveTickColor = ApertureTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val activeTickColor = ApertureTheme.colorScheme.onPrimary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .onKeyEvent { event ->
                    if (!isFocused || !enabled || event.type != KeyEventType.KeyDown) {
                        return@onKeyEvent false
                    }

                    val stepSize = if (steps > 0) {
                        totalRange / (steps + 1)
                    } else {
                        totalRange / 20f
                    }

                    when (event.key) {
                        Key.DirectionLeft -> {
                            onValueChange((clampedValue - stepSize).coerceIn(valueRange))
                            true
                        }
                        Key.DirectionRight -> {
                            onValueChange((clampedValue + stepSize).coerceIn(valueRange))
                            true
                        }
                        else -> false
                    }
                }
                .focusable(enabled),
            contentAlignment = Alignment.CenterStart
        ) {
            val density = LocalDensity.current
            val widthPx = constraints.maxWidth.toFloat()
            val selectorWidth = with(density) { 4.dp.toPx() }
            val selectorHalfWidth = selectorWidth / 2f
            val trackHeight = with(density) { 16.dp.toPx() }
            val selectorHeight = with(density) { 44.dp.toPx() }
            val trackCenterY = with(density) { 26.dp.toPx() }
            val trackStart = selectorHalfWidth
            val trackEnd = widthPx - selectorHalfWidth
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
            val currentX = trackStart + (animatedProgress * trackWidth)
            val neutralX = trackStart + (neutralProgress * trackWidth)
            val selectorGap = with(density) { 6.dp.toPx() }
            val fillStart = if (neutralValue != null) minOf(currentX, neutralX) else trackStart
            val fillEnd = if (neutralValue != null) maxOf(currentX, neutralX) else currentX
            val trackCorner = trackHeight / 2f

            Canvas(modifier = Modifier.fillMaxWidth()) {
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(trackStart, trackCenterY - trackHeight / 2f),
                    size = Size(trackWidth, trackHeight),
                    cornerRadius = CornerRadius(trackCorner)
                )

                if (abs(fillEnd - fillStart) > 0.5f) {
                    drawRoundRect(
                        color = activeTrackColor,
                        topLeft = Offset(fillStart, trackCenterY - trackHeight / 2f),
                        size = Size(fillEnd - fillStart, trackHeight),
                        cornerRadius = CornerRadius(trackCorner)
                    )
                }

                if (showTicks) {
                    val intervals = tickCount - 1
                    repeat(tickCount) { index ->
                        val tickProgress = index.toFloat() / intervals
                        val tickX = trackStart + tickProgress * trackWidth
                        val isActive = if (neutralValue != null) {
                            tickProgress in minOf(neutralProgress, animatedProgress)..maxOf(neutralProgress, animatedProgress)
                        } else {
                            tickProgress <= animatedProgress
                        }

                        drawCircle(
                            color = if (isActive) activeTickColor else inactiveTickColor,
                            radius = with(density) { 2.dp.toPx() },
                            center = Offset(tickX, trackCenterY)
                        )
                    }
                }

                neutralValue?.let {
                    drawRoundRect(
                        color = activeTrackColor,
                        topLeft = Offset(
                            neutralX - with(density) { 1.dp.toPx() },
                            trackCenterY - with(density) { 10.dp.toPx() }
                        ),
                        size = Size(with(density) { 2.dp.toPx() }, with(density) { 20.dp.toPx() }),
                        cornerRadius = CornerRadius(with(density) { 1.dp.toPx() })
                    )
                }

                val selectorLeft = currentX - selectorGap
                val selectorRight = currentX + selectorGap
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(
                        selectorLeft,
                        trackCenterY - trackHeight / 2f - with(density) { 1.dp.toPx() }
                    ),
                    size = Size(selectorGap * 2f, trackHeight + with(density) { 2.dp.toPx() }),
                    cornerRadius = CornerRadius((trackHeight + with(density) { 2.dp.toPx() }) / 2f)
                )

                if (neutralValue != null && currentX > neutralX) {
                    val leftEnd = minOf(fillEnd, selectorLeft)
                    if (leftEnd > fillStart) {
                        drawRoundRect(
                            color = activeTrackColor,
                            topLeft = Offset(fillStart, trackCenterY - trackHeight / 2f),
                            size = Size(leftEnd - fillStart, trackHeight),
                            cornerRadius = CornerRadius(trackCorner)
                        )
                    }
                }

                if (neutralValue != null && currentX < neutralX) {
                    val rightStart = maxOf(fillStart, selectorRight)
                    if (fillEnd > rightStart) {
                        drawRoundRect(
                            color = activeTrackColor,
                            topLeft = Offset(rightStart, trackCenterY - trackHeight / 2f),
                            size = Size(fillEnd - rightStart, trackHeight),
                            cornerRadius = CornerRadius(trackCorner)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (currentX - selectorHalfWidth).roundToInt(),
                            (trackCenterY - selectorHeight / 2f).roundToInt()
                        )
                    }
                    .size(width = 4.dp, height = 44.dp)
                    .graphicsLayer {
                        scaleX = animatedSelectorScale
                        scaleY = animatedSelectorScale
                    }
                    .background(activeTrackColor, RoundedCornerShape(percent = 50))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatSliderValue(valueRange.start), color = ApertureTheme.colorScheme.onSurfaceVariant)
            Text(formatSliderValue(valueRange.endInclusive), color = ApertureTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatSliderValue(value: Float): String {
    val rounded = value.roundToInt()
    return if (abs(value - rounded) < 0.001f) {
        rounded.toString()
    } else {
        "%.2f".format(value).trimEnd('0').trimEnd('.')
    }
}
