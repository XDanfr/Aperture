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
class ApertureMotion(
    private val focusStiffness: Float = 300f,
    private val focusDamping: Float = 0.68f,
    private val heroStiffness: Float = Spring.StiffnessLow,
    private val heroDamping: Float = Spring.DampingRatioNoBouncy,
    private val enterStiffness: Float = Spring.StiffnessMedium,
    private val enterDamping: Float = Spring.DampingRatioNoBouncy,
    private val playbackOverlayDuration: Int = 300,
    private val playbackProgressDuration: Int = 150,
    private val exitDuration: Int = 200
) {
    /**
     * Fluid spring for scaling components on focus.
     */
    fun <T> focus(): AnimationSpec<T> = spring(
        dampingRatio = focusDamping,
        stiffness = focusStiffness
    )

    /**
     * Responsive spring for large hero content transitions.
     */
    fun <T> hero(): AnimationSpec<T> = spring(
        dampingRatio = heroDamping,
        stiffness = heroStiffness
    )

    /**
     * Standard tween for playback overlay visibility.
     */
    fun <T> playbackOverlay(): AnimationSpec<T> = tween(durationMillis = playbackOverlayDuration)

    /**
     * Precise tween for seek bars and progress indicators.
     */
    fun <T> playbackProgress(): AnimationSpec<T> = tween(durationMillis = playbackProgressDuration)

    /**
     * Expressive enter transition for new screens or dialogs.
     */
    fun <T> enter(): AnimationSpec<T> = spring(
        dampingRatio = enterDamping,
        stiffness = enterStiffness
    )

    /**
     * Smooth exit transition for dismissing content.
     */
    fun <T> exit(): AnimationSpec<T> = tween(durationMillis = exitDuration)
}
