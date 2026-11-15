package me.xdan.aperture.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.xdan.aperture.ui.theme.ApertureThemeOption
import me.xdan.aperture.ui.theme.DynamicThemeId

/**
 * theme preview: primary across the top half, secondary and tertiary quartering the
 * bottom. artwork dynamic shows a hue sweep. shared by the TV and phone theme pickers.
 */
@Composable
fun ApertureThemeSwatch(
    option: ApertureThemeOption,
    outlineColor: Color,
    modifier: Modifier = Modifier,
    outlineWidth: Dp = 1.dp
) {
    Canvas(modifier) {
        val strokeWidth = outlineWidth.toPx()
        if (option.id == DynamicThemeId) {
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                )
            )
        } else {
            drawArc(color = option.primary, startAngle = 180f, sweepAngle = 180f, useCenter = true)
            drawArc(color = option.secondary, startAngle = 90f, sweepAngle = 90f, useCenter = true)
            drawArc(color = option.tertiary, startAngle = 0f, sweepAngle = 90f, useCenter = true)
        }
        drawCircle(
            color = outlineColor,
            radius = size.minDimension / 2f - strokeWidth / 2f,
            style = Stroke(width = strokeWidth)
        )
    }
}
