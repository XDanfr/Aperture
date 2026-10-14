package me.xdan.aperture.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import me.xdan.aperture.ui.theme.tokens.ApertureMotion
import kotlin.math.roundToInt

/** A full-ring spring rushes hundreds of degrees before settling. Cruise at a readable speed. */
internal const val BrandSpinDegreesPerSecond = 180f
internal const val BrandSpinAccelerationMillis = 240
internal const val BrandSpinSettleDegrees = 8f
internal const val BrandSpinAccelerationDegrees =
    BrandSpinDegreesPerSecond * BrandSpinAccelerationMillis / 2_000f

internal val BrandSpinAccelerationSpec = tween<Float>(
    durationMillis = BrandSpinAccelerationMillis,
    easing = Easing { progress -> progress * progress }
)
internal val BrandSpinCruiseSpec = tween<Float>(
    durationMillis = ((360f - BrandSpinSettleDegrees - BrandSpinAccelerationDegrees) /
        BrandSpinDegreesPerSecond * 1_000f).roundToInt(),
    easing = LinearEasing
)

/** Acceleration → fixed-speed turn → a small official Expressive spring settle. */
internal suspend fun Animatable<Float, AnimationVector1D>.animateBrandTurn(motion: ApertureMotion) {
    val start = value % 360f
    snapTo(start)
    animateTo(start + BrandSpinAccelerationDegrees, BrandSpinAccelerationSpec)
    animateTo(start + 360f - BrandSpinSettleDegrees, BrandSpinCruiseSpec)
    animateTo(
        start + 360f,
        motion.brandSlowSpatial(),
        initialVelocity = BrandSpinDegreesPerSecond
    )
    // Equivalent orientation; keep successive turns numerically small.
    snapTo(start)
}
