package me.xdan.aperture.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A Material 3 slider track drawn as separate rounded bars, split at [splits] (fractions), with
 * the usual gap either side of the thumb at [value]. For the companion's scrubbable bar.
 */
@Composable
fun SplitSliderTrack(
    value: Float,
    splits: List<Float>,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    gap: Dp = 4.dp,
    thumbGap: Dp = 6.dp,
    innerCorner: Dp = 4.dp,
    stopIndicatorSize: Dp = 4.dp
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val h = size.height
        val outer = CornerRadius(h / 2f)
        val inner = CornerRadius(innerCorner.toPx().coerceAtMost(h / 2f))
        val gapPx = gap.toPx()
        val thumbX = size.width * value.coerceIn(0f, 1f)
        val thumbHalf = thumbGap.toPx()
        val bounds = (listOf(0f) + splits.filter { it > 0f && it < 1f }.sorted() + listOf(1f)).map { it * size.width }
        for (i in 0 until bounds.size - 1) {
            val start = bounds[i] + if (i > 0) gapPx / 2f else 0f
            val end = bounds[i + 1] - if (i < bounds.size - 2) gapPx / 2f else 0f
            if (end <= start) continue
            // Each piece is a full pill; the thumb cuts it into an active and an inactive part.
            fun pill(from: Float, to: Float, color: Color, roundStart: Boolean, roundEnd: Boolean) {
                if (to - from < 0.5f) return
                val path = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = from, top = 0f, right = to, bottom = h,
                            topLeftCornerRadius = if (roundStart) outer else inner,
                            bottomLeftCornerRadius = if (roundStart) outer else inner,
                            topRightCornerRadius = if (roundEnd) outer else inner,
                            bottomRightCornerRadius = if (roundEnd) outer else inner
                        )
                    )
                }
                drawPath(path, color)
            }
            val activeEnd = (thumbX - thumbHalf).coerceIn(start, end)
            val inactiveStart = (thumbX + thumbHalf).coerceIn(start, end)
            pill(start, activeEnd, activeColor, roundStart = true, roundEnd = activeEnd >= end)
            pill(inactiveStart, end, inactiveColor, roundStart = inactiveStart <= start, roundEnd = true)
        }
        // M3 Expressive's stop indicator: a dot inside the track's end, until the thumb reaches it.
        val dot = stopIndicatorSize.toPx()
        val dotX = size.width - h / 2f
        if (thumbX + thumbHalf < dotX - dot / 2f) drawCircle(activeColor, radius = dot / 2f, center = Offset(dotX, h / 2f))
    }
}
