package me.xdan.aperture.ui.component.expressive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.xdan.aperture.ui.theme.ApertureTheme

/** Temporary migration shim. Prefer LinearWavyProgressIndicator directly. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Deprecated("Use androidx.compose.material3.LinearWavyProgressIndicator directly.")
@Composable
fun ExpressiveWavyProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = ApertureTheme.colorScheme.primary,
    trackColor: Color = ApertureTheme.colorScheme.surfaceContainerHigh,
    strokeWidth: Dp = 6.dp
) {
    val stroke = with(LocalDensity.current) { Stroke(width = strokeWidth.toPx()) }
    if (progress == null) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            LinearWavyProgressIndicator(
                color = color,
                trackColor = trackColor,
                stroke = stroke
            )
        }
    } else {
        LinearWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            stroke = stroke
        )
    }
}
