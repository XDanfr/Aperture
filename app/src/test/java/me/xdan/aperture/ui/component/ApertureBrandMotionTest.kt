package me.xdan.aperture.ui.component

import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.getVelocityFromNanos
import me.xdan.aperture.ui.theme.tokens.ApertureMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ApertureBrandMotionTest {
    @Test
    fun fullTurnKeepsAReadableSpeedAndOnlyBouncesAtTheEnd() {
        val acceleration = TargetBasedAnimation(
            BrandSpinAccelerationSpec, Float.VectorConverter,
            0f, BrandSpinAccelerationDegrees
        )
        val cruise = TargetBasedAnimation(
            BrandSpinCruiseSpec, Float.VectorConverter,
            BrandSpinAccelerationDegrees, 360f - BrandSpinSettleDegrees
        )
        val settle = TargetBasedAnimation(
            ApertureMotion().brandSlowSpatial(), Float.VectorConverter,
            360f - BrandSpinSettleDegrees, 360f, BrandSpinDegreesPerSecond
        )
        val animations = listOf(acceleration, cruise, settle)
        animations.forEach { animation ->
            var time = 0L
            while (time <= animation.durationNanos) {
                assertTrue("Rotation exceeded the configured speed", abs(animation.getVelocityFromNanos(time)) <= 181f)
                if (animation === settle) {
                    val angle = animation.getValueFromNanos(time)
                    assertTrue("Settle should stay close to the final orientation", angle in 352f..366f)
                }
                time += 4_000_000L
            }
        }
        // Cruise fills the duration, instead of one fast rush followed by a long spring tail.
        assertEquals(180f, cruise.getVelocityFromNanos(cruise.durationNanos / 2), 0.2f)
        val durationMillis = animations.sumOf { it.durationNanos } / 1_000_000
        assertTrue("A turn should take around 2–3 seconds", durationMillis in 2_000..3_000)
    }
}
