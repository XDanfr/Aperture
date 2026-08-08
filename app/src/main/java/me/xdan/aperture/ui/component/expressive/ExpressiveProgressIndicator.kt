package me.xdan.aperture.ui.component.expressive

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.xdan.aperture.ui.theme.ApertureTheme

/**
 * A Material 3 Expressive linear progress indicator.
 *
 * Features fully rounded endpoints, smooth transitions, and the official
 * "upwards pop" animation when the progress value increases.
 */
@Composable
fun ExpressiveProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = ApertureTheme.colorScheme.primary,
    trackColor: Color = ApertureTheme.colorScheme.surfaceContainerHigh
) {
    // 1. Progress Animation Scheme: Spring(StiffnessVeryLow, DampingRatioNoBouncy)
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            stiffness = Spring.StiffnessVeryLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "progressAnimation"
    )

    // 2. Upwards Pop: Expansion on increase
    var previousProgress by remember { mutableFloatStateOf(progress) }
    var scaleYTarget by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(progress) {
        if (progress > previousProgress) {
            scaleYTarget = 1.4f
            kotlinx.coroutines.delay(120)
            scaleYTarget = 1f
        }
        previousProgress = progress
    }

    val animatedHeightScale by animateFloatAsState(
        targetValue = scaleYTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "heightPop"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleY = animatedHeightScale
            }
            .clip(CircleShape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color)
        )
    }
}
