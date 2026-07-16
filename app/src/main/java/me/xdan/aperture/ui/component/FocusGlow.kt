package me.xdan.aperture.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme

private const val FocusGlowAlpha = 0.46f
private const val FocusGlowInDurationMillis = 220
private const val FocusGlowOutDurationMillis = 160
private val FocusGlowElevation = 18.dp

@Composable
internal fun rememberFocusGlow(isFocused: Boolean): Glow {
    val durationMillis = if (isFocused) {
        FocusGlowInDurationMillis
    } else {
        FocusGlowOutDurationMillis
    }
    val progress by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "focusGlowProgress"
    )

    return Glow(
        elevationColor = MaterialTheme.colorScheme.primary.copy(
            alpha = FocusGlowAlpha * progress
        ),
        elevation = FocusGlowElevation * progress
    )
}
