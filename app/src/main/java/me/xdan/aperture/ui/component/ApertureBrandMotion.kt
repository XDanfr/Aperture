package me.xdan.aperture.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import me.xdan.aperture.ui.theme.tokens.ApertureMotion

/** The original logo's quick, even turn. */
internal const val BrandSpinDurationMillis = 650
internal const val BrandSpinBounceDegrees = 4f
internal val BrandSpinEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** Original 650 ms spin, followed by a restrained Expressive spring settle. */
internal suspend fun Animatable<Float, AnimationVector1D>.animateBrandTurn(motion: ApertureMotion) {
    val start = value % 360f
    snapTo(start)
    animateTo(
        targetValue = start + 360f,
        animationSpec = tween(BrandSpinDurationMillis, easing = BrandSpinEasing)
    )
    // A few degrees of spring overshoot give the turn a soft, physical landing.
    animateTo(start + 360f + BrandSpinBounceDegrees, motion.brandFastSpatial())
    animateTo(start + 360f, motion.brandFastSpatial())
    snapTo(start)
}
