package me.xdan.aperture.ui.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.isActive

/**
 * A wavy playback bar that can split into pieces at [splits] (fractions of the runtime, e.g. a
 * Jellyfin intro's start and end). Only the piece playing now waves, and only while [wavy]; the
 * rest are flat. Shared by the TV player and the companion's Now Playing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplitWaveProgress(
    progress: () -> Float,
    wavy: Boolean,
    indicatorColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    splits: List<Float> = emptyList(),
    amplitudeSpec: AnimationSpec<Float> = tween(300)
) {
    val amplitude by animateFloatAsState(
        targetValue = if (wavy) 1f else 0f,
        animationSpec = amplitudeSpec,
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
                // Split points too close to each other or an end would leave slivers.
                val minPiece = gapPx * 3f / width.coerceAtLeast(1f)
                val bounds = buildList {
                    add(0f)
                    splits.sorted().forEach { split -> if (split - last() > minPiece && 1f - split > minPiece) add(split) }
                    add(1f)
                }
                onDrawBehind {
                    val fraction = progress().coerceIn(0f, 1f)
                    val offset = phase * wavelengthPx
                    val halfGap = if (bounds.size > 2) gapPx else 0f
                    scale(if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, 1f) {
                        for (i in 0 until bounds.size - 1) {
                            // Pieces meet with a gap; the bar's own ends stay at the edges.
                            val pieceStart = inset + width * bounds[i] + if (i > 0) halfGap else 0f
                            val pieceEnd = inset + width * bounds[i + 1] - if (i < bounds.size - 2) halfGap else 0f
                            if (pieceEnd <= pieceStart) continue
                            val played = (inset + width * fraction).coerceIn(pieceStart, pieceEnd)
                            val isCurrent = fraction > bounds[i] && fraction <= bounds[i + 1]
                            val trackStart = if (played <= pieceStart) pieceStart else played + gapPx
                            if (trackStart < pieceEnd) {
                                drawLine(trackColor, Offset(trackStart, center), Offset(pieceEnd, center), trackStroke.width, trackStroke.cap)
                            }
                            if (played <= pieceStart) continue
                            if (isCurrent && amplitude > 0f) {
                                // Only the piece playing now waves; it shares one wave with the whole bar.
                                segment.rewind()
                                measure.getSegment((offset + pieceStart - inset) * pathScale, (offset + played - inset) * pathScale, segment)
                                matrix.reset()
                                matrix.scale(1f, amplitude.coerceIn(0f, 1f))
                                segment.transform(matrix)
                                segment.translate(Offset(inset - offset, center))
                                drawPath(segment, indicatorColor, style = stroke)
                            } else {
                                drawLine(indicatorColor, Offset(pieceStart, center), Offset(played, center), stroke.width, stroke.cap)
                            }
                        }
                    }
                }
            }
    )
}
