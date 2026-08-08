package me.xdan.aperture.ui.component.expressive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.xdan.aperture.ui.theme.ApertureTheme
import kotlin.math.roundToInt

/**
 * A tactile, TV-optimized toggle component inspired by Material 3 Expressive principles.
 *
 * Features refined states where the deselected thumb is smaller and muted.
 * Uses physics-based motion for a high-end feel.
 */
@Composable
fun ExpressiveToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isFocused: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            if (enabled) ApertureTheme.colorScheme.primary else ApertureTheme.colorScheme.primary.copy(alpha = 0.38f)
        } else {
            ApertureTheme.colorScheme.surfaceContainerHigh
        },
        label = "trackColor"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (checked) {
            ApertureTheme.colorScheme.onPrimary
        } else {
            ApertureTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        label = "thumbColor"
    )

    val thumbSize by animateDpAsState(
        targetValue = if (checked) 24.dp else 16.dp,
        animationSpec = ApertureTheme.motion.focus(),
        label = "thumbSize"
    )

    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 20f else 0f,
        animationSpec = ApertureTheme.motion.focus(),
        label = "thumbOffset"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = ApertureTheme.motion.focus(),
        label = "toggleScale"
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(32.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clip(CircleShape)
            .background(trackColor)
            .then(
                if (onCheckedChange != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = { onCheckedChange(!checked) }
                    )
                } else Modifier
            )
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, ApertureTheme.colorScheme.border, CircleShape)
                } else {
                    Modifier
                }
            )
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { 
                    // Adjust horizontal offset based on thumb size to keep it centered within the 4dp padding
                    val x = if (checked) thumbOffset.dp.roundToPx() else (thumbOffset.dp + 4.dp).roundToPx()
                    IntOffset(x, 0)
                }
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}
