package me.xdan.aperture.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import me.xdan.aperture.ui.theme.ApertureTheme

private const val FocusGlowAlpha = 0.46f
private val FocusGlowElevation = 18.dp

@Composable
internal fun Modifier.focusGlow(isFocused: Boolean): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = if (isFocused) {
            ApertureTheme.motion.focusGlowEnter()
        } else {
            ApertureTheme.motion.focusGlowExit()
        },
        label = "focusGlowProgress"
    )

    val glowColor = ApertureTheme.colorScheme.primary.copy(
        alpha = FocusGlowAlpha * progress
    )

    return shadow(
        elevation = FocusGlowElevation * progress,
        shape = ApertureTheme.shapes.poster,
        clip = false,
        ambientColor = glowColor,
        spotColor = glowColor
    )
}
