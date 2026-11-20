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

/**
 * Where the bar splits, as fractions of the runtime: the starts and ends of the title's Jellyfin
 * segments (intro, credits…). Empty for one unbroken bar.
 */
internal val LocalProgressSplits = compositionLocalOf { emptyList<Float>() }

/** Playback needs amplitude transitions that can reverse before their previous animation finishes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaybackWaveProgress(
    progress: () -> Float,
    wavy: Boolean,
    modifier: Modifier = Modifier,
    splits: List<Float> = LocalProgressSplits.current
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
                    val halfGap = if (bounds.size > 2) gapPx / 2f else 0f
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
