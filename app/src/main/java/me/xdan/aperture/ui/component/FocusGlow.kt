package me.xdan.aperture.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Glow
import me.xdan.aperture.ui.theme.ApertureTheme

private const val FocusGlowAlpha = 0.46f
private val FocusGlowElevation = 18.dp

@Composable
internal fun rememberFocusGlow(isFocused: Boolean): Glow {
    val motion = ApertureTheme.motion
    val progress by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = if (isFocused) {
            motion.focusGlowEnter()
        } else {
            motion.focusGlowExit()
        },
        label = "focusGlowProgress"
    )

    return Glow(
        elevationColor = ApertureTheme.colorScheme.primary.copy(
            alpha = FocusGlowAlpha * progress
        ),
        elevation = FocusGlowElevation * progress
    )
}
