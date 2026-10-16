package me.xdan.aperture.ui.screen.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.isActive
import me.xdan.aperture.ui.theme.ApertureTheme

/** Playback needs amplitude transitions that can reverse before their previous animation finishes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaybackWaveProgress(
    progress: () -> Float,
    wavy: Boolean,
    modifier: Modifier = Modifier
) {
    val amplitude by animateFloatAsState(
        targetValue = if (wavy) 1f else 0f,
        animationSpec = ApertureTheme.motion.enter(),
        label = "playbackWaveAmplitude"
    )
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(wavy) {
        if (!wavy) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            phase = (phase + (frame - previousFrame) / 1_000_000_000f) % 1f
            previousFrame = frame
        }
    }
    val indicatorColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val stroke = WavyProgressIndicatorDefaults.linearIndicatorStroke
    val trackStroke = WavyProgressIndicatorDefaults.linearTrackStroke
    val wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength
    val gap = WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize
    Spacer(
        modifier.height(WavyProgressIndicatorDefaults.LinearContainerHeight)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f)
            }
            .drawWithCache {
                val center = size.height / 2f
                val inset = maxOf(stroke.width, trackStroke.width) / 2f
                val width = (size.width - inset * 2f).coerceAtLeast(0f)
                val wavelengthPx = wavelength.toPx()
                val halfWave = wavelengthPx / 2f
                val fullPath = Path().apply {
                    moveTo(0f, 0f)
                    var x = halfWave
                    var controlY = (size.height - stroke.width).coerceAtLeast(0f)
                    while (x <= width + wavelengthPx * 2f) {
                        quadraticTo(x - halfWave / 2f, controlY, x, 0f)
                        controlY = -controlY
                        x += halfWave
                    }
                }
                val measure = PathMeasure().apply { setPath(fullPath, false) }
                val pathScale = measure.length / fullPath.getBounds().width.coerceAtLeast(1f)
                val segment = Path()
                val matrix = Matrix()
                val gapPx = gap.toPx() + (stroke.width + trackStroke.width) / 2f
                onDrawBehind {
                    val fraction = progress().coerceIn(0f, 1f)
                    val end = inset + width * fraction
                    val trackStart = if (fraction == 0f) inset else end + gapPx
                    val offset = phase * wavelengthPx
                    segment.rewind()
                    measure.getSegment(offset * pathScale, (offset + width * fraction) * pathScale, segment)
                    matrix.reset()
                    matrix.scale(1f, amplitude.coerceIn(0f, 1f))
                    segment.transform(matrix)
                    segment.translate(Offset(inset - offset, center))
                    scale(if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, 1f) {
                        if (trackStart < size.width - inset) {
                            drawLine(trackColor, Offset(trackStart, center), Offset(size.width - inset, center), trackStroke.width, trackStroke.cap)
                        }
                        if (fraction > 0f) drawPath(segment, indicatorColor, style = stroke)
                    }
                }
            }
    )
}
