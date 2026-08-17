package me.xdan.aperture.ui.component.expressive

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.xdan.aperture.ui.theme.ApertureTheme

/** Temporary migration shim. Prefer LinearProgressIndicator directly. */
@Deprecated("Use androidx.compose.material3.LinearProgressIndicator directly.")
@Composable
fun ExpressiveProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = ApertureTheme.colorScheme.primary,
    trackColor: Color = ApertureTheme.colorScheme.surfaceContainerHigh
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.height(height),
        color = color,
        trackColor = trackColor
    )
}
