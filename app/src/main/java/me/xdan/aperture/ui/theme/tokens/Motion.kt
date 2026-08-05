package me.xdan.aperture.ui.theme.tokens

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Semantic motion tokens for Aperture.
 *
 * Motion in Aperture is physics-based (Springs) for focus interactions
 * and precise (Tweens) for playback controls.
 */
data class ApertureMotion(
    /**
     * Fluid spring for scaling components on focus.
     * Low stiffness and medium damping provide a natural "pop".
     */
    val focus: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    ),

    /**
     * Responsive spring for large hero content transitions.
     */
    val hero: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    ),

    /**
     * Standard tween for playback overlay visibility.
     */
    val playbackOverlay: AnimationSpec<Float> = tween(durationMillis = 300),

    /**
     * Precise tween for seek bars and progress indicators.
     */
    val playbackProgress: AnimationSpec<Float> = tween(durationMillis = 150),

    /**
     * Expressive enter transition for new screens or dialogs.
     */
    val enter: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    ),

    /**
     * Smooth exit transition for dismissing content.
     */
    val exit: AnimationSpec<Float> = tween(durationMillis = 200)
)
