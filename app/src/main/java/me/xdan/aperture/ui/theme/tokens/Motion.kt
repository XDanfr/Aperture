package me.xdan.aperture.ui.theme.tokens

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

/**
 * Semantic motion tokens for Aperture.
 *
 * Motion in Aperture uses expressive easing and springs for spatial/focus
 * interactions, while playback and utility transitions stay precise.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ApertureMotion(
    private val focusStiffness: Float = 400f,
    private val focusDamping: Float = 0.75f,
    private val heroStiffness: Float = Spring.StiffnessLow,
    private val heroDamping: Float = Spring.DampingRatioNoBouncy,
    private val enterStiffness: Float = Spring.StiffnessMedium,
    private val enterDamping: Float = Spring.DampingRatioNoBouncy,
    private val playbackOverlayDuration: Int = 300,
    private val playbackProgressDuration: Int = 150,
    private val exitDuration: Int = 200,
    private val focusGlowEnterDuration: Int = 220,
    private val focusGlowExitDuration: Int = 160,
    val expressiveLoadingCycleDuration: Int = 5000,
    val expressiveLoadingRotationDuration: Int = 2500,
    val expressiveLoadingMorphRotation: Float = 180f
) {
    private val brandScheme = MotionScheme.expressive()

    /** Official M3 Expressive spatial tokens: rotation, geometry and size may overshoot. */
    fun <T> brandSpatial(): FiniteAnimationSpec<T> = brandScheme.defaultSpatialSpec()
    fun <T> brandFastSpatial(): FiniteAnimationSpec<T> = brandScheme.fastSpatialSpec()
    fun <T> brandSlowSpatial(): FiniteAnimationSpec<T> = brandScheme.slowSpatialSpec()

    /** Official effects tokens settle opacity without spatial bounce. */
    fun <T> brandEffects(): FiniteAnimationSpec<T> = brandScheme.defaultEffectsSpec()
    fun <T> brandFastEffects(): FiniteAnimationSpec<T> = brandScheme.fastEffectsSpec()

    /** Emphasized M3 easing: quick movement followed by a confident settle. */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Accelerating easing for elements leaving an interaction state. */
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** Fluid spring for scaling components on focus. */
    fun <T> focus(): AnimationSpec<T> = spring(
        dampingRatio = focusDamping,
        stiffness = focusStiffness
    )

    /** Responsive spring for large hero content transitions. */
    fun <T> hero(): AnimationSpec<T> = spring(
        dampingRatio = heroDamping,
        stiffness = heroStiffness
    )

    /** Standard tween for playback overlay visibility. */
    fun <T> playbackOverlay(): AnimationSpec<T> = tween(durationMillis = playbackOverlayDuration)

    /** Precise tween for seek bars and progress indicators. */
    fun <T> playbackProgress(): AnimationSpec<T> = tween(durationMillis = playbackProgressDuration)

    /** Expressive enter transition for new screens or dialogs. */
    fun <T> enter(): FiniteAnimationSpec<T> = spring(
        dampingRatio = enterDamping,
        stiffness = enterStiffness
    )

    /** Smooth exit transition for dismissing content. */
    fun <T> exit(): FiniteAnimationSpec<T> = tween(durationMillis = exitDuration, easing = emphasizedAccelerate)

    /** Quick, emphasized focus-glow entrance. */
    fun <T> focusGlowEnter(): AnimationSpec<T> = tween(
        durationMillis = focusGlowEnterDuration,
        easing = emphasized
    )

    /** Slightly quicker focus-glow exit, with deliberate acceleration away. */
    fun <T> focusGlowExit(): AnimationSpec<T> = tween(
        durationMillis = focusGlowExitDuration,
        easing = emphasizedAccelerate
    )
}
