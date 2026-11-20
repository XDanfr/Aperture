package me.xdan.aperture.mobile.ui.screens

import me.xdan.aperture.mobile.ui.components.LocalDeviceNoun
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.produceState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.xdan.aperture.companion.protocol.CompanionCommand
import me.xdan.aperture.mobile.data.LiveNowPlaying
import me.xdan.aperture.mobile.ui.components.Artwork
import me.xdan.aperture.ui.component.SplitWaveProgress
import me.xdan.aperture.ui.component.SplitSliderTrack
import me.xdan.aperture.companion.protocol.CompanionChapter
import androidx.compose.ui.graphics.ImageBitmap
import me.xdan.aperture.companion.protocol.NowPlaying
import me.xdan.aperture.mobile.ui.components.CompanionShapes

/**
 * playback progress, updated every frame between TV updates.
 * [fraction] eases towards the real position so seeks glide instead of jumping.
 */
@Stable
private class SmoothProgress {
    var positionMs by mutableLongStateOf(-1L)
    var fraction by mutableFloatStateOf(-1f)
}

/** time constant of the easing; steady playback trails by about this much, invisibly. */
private const val PROGRESS_EASE_MS = 140f

@Composable
private fun rememberSmoothProgress(live: LiveNowPlaying): SmoothProgress {
    val progress = remember { SmoothProgress() }
    val current = rememberUpdatedState(live)
    LaunchedEffect(Unit) {
        var lastFrame = 0L
        while (true) {
            val snapshot = current.value
            var settled = false
            withFrameMillis { frame ->
                val position = snapshot.positionAt(SystemClock.elapsedRealtime())
                if (progress.positionMs < 0 || position / 1000 != progress.positionMs / 1000) progress.positionMs = position
                val duration = snapshot.value.durationMs
                val target = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                val dt = if (lastFrame == 0L) 0L else frame - lastFrame
                lastFrame = frame
                val from = progress.fraction
                progress.fraction = if (from < 0f) target else from + (target - from) * (1f - exp(-dt / PROGRESS_EASE_MS))
                settled = !snapshot.value.isPlaying && abs(target - progress.fraction) < 0.0005f
                if (settled) progress.fraction = target
            }
            if (settled) {
                // paused and caught up: sleep until the TV reports something new.
                snapshotFlow { current.value }.first { it !== snapshot }
                lastFrame = 0L
            }
        }
    }
    return progress
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingBar(
    live: LiveNowPlaying,
    onExpand: () -> Unit,
    onCommand: (CompanionCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val np = live.value
    val progress = rememberSmoothProgress(live)
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()
    ) {
        Column(Modifier.clickable(onClick = onExpand)) {
            Row(
                Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(
                    np.posterPath,
                    size = "w154",
                    modifier = Modifier.size(width = 40.dp, height = 60.dp).clip(MaterialTheme.shapes.small)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(np.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        np.subtitle ?: "Playing on ${LocalDeviceNoun.current}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { onCommand(CompanionCommand.SeekBy(-10_000)) }) {
                    Icon(Icons.Rounded.Replay10, contentDescription = "Back 10 seconds")
                }
                FilledIconButton(
                    onClick = { onCommand(CompanionCommand.TogglePlayback) },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (np.isPlaying) "Pause" else "Play"
                    )
                }
            }
            SplitWaveProgress(
                progress = { progress.fraction.coerceAtLeast(0f) },
                wavy = np.isPlaying,
                indicatorColor = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                splits = np.splitFractions(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp, top = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingSheet(
    live: LiveNowPlaying,
    onDismiss: () -> Unit,
    onCommand: (CompanionCommand) -> Unit,
    loadFrame: suspend (Long) -> ImageBitmap? = { null }
) {
    val np = live.value
    val progress = rememberSmoothProgress(live)
    val position = progress.positionMs.coerceAtLeast(0L)
    var showChapters by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(CompanionShapes.hero)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Artwork(np.backdropPath ?: np.posterPath, size = "w780", modifier = Modifier.matchParentSize())
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Playing on your ${LocalDeviceNoun.current}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(np.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                np.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val duration = np.durationMs.coerceAtLeast(1)
            Column(Modifier.widthIn(max = 520.dp)) {
                Slider(
                    value = if (scrubbing) scrubValue else progress.fraction.coerceIn(0f, 1f),
                    onValueChange = { scrubbing = true; scrubValue = it },
                    onValueChangeFinished = {
                        onCommand(CompanionCommand.SeekTo((scrubValue * duration).toLong()))
                        // hold the thumb where it was dropped rather than easing back from the old spot.
                        progress.fraction = scrubValue
                        scrubbing = false
                    },
                    enabled = np.durationMs > 0,
                    track = { state ->
                        // Separate rounded bars, split where the TV's chapters (intro, credits…) start.
                        SplitSliderTrack(
                            value = state.value,
                            splits = np.splitFractions(),
                            activeColor = MaterialTheme.colorScheme.primary,
                            inactiveColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        formatClock(if (scrubbing) (scrubValue * duration).toLong() else position),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.weight(1f))
                    Text(formatClock(np.durationMs), style = MaterialTheme.typography.labelMedium)
                }
                np.chapters.chapterAt(if (scrubbing) (scrubValue * duration).toLong() else position)?.let { chapter ->
                    ChapterChip(name = chapter.name, onClick = { showChapters = true })
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalIconButton(
                    onClick = { onCommand(CompanionCommand.SeekBy(-10_000)) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize())
                ) { Icon(Icons.Rounded.Replay10, contentDescription = "Back 10 seconds") }
                // hero control: the large play/pause button morphs shape when pressed.
                FilledIconButton(
                    onClick = { onCommand(CompanionCommand.TogglePlayback) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(IconButtonDefaults.largeContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide))
                ) {
                    Icon(
                        if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (np.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(IconButtonDefaults.largeIconSize)
                    )
                }
                FilledTonalIconButton(
                    onClick = { onCommand(CompanionCommand.SeekBy(10_000)) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize())
                ) { Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10 seconds") }
            }
            np.skipLabel?.let { label ->
                // Its own row: beside Stop and Next episode it doesn't fit a phone's width.
                Button(
                    onClick = { onCommand(CompanionCommand.SkipSegment) },
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onCommand(CompanionCommand.Stop); onDismiss() }) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
                if (np.hasNextEpisode) {
                    OutlinedButton(onClick = { onCommand(CompanionCommand.NextEpisode) }) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Next episode")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    if (showChapters && np.chapters.isNotEmpty()) {
        ChaptersSheet(
            chapters = np.chapters,
            current = np.chapters.chapterAt(position),
            loadFrame = loadFrame,
            onPick = { chapter ->
                onCommand(CompanionCommand.SeekTo(chapter.startMs))
                progress.fraction = if (np.durationMs > 0) chapter.startMs.toFloat() / np.durationMs else progress.fraction
                showChapters = false
            },
            onDismiss = { showChapters = false }
        )
    }
}

/** The chapter playing now, with an arrow that opens the list of them (as on YouTube). */
@Composable
private fun ChapterChip(name: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Chapters", modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersSheet(
    chapters: List<CompanionChapter>,
    current: CompanionChapter?,
    loadFrame: suspend (Long) -> ImageBitmap?,
    onPick: (CompanionChapter) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text("Chapters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            items(chapters, key = { it.startMs }) { chapter ->
                ChapterRow(chapter, isCurrent = chapter == current, loadFrame = loadFrame, onClick = { onPick(chapter) })
            }
        }
    }
}

@Composable
internal fun ChapterRow(chapter: CompanionChapter, isCurrent: Boolean, loadFrame: suspend (Long) -> ImageBitmap?, onClick: () -> Unit) {
    // A frame a few seconds in: the very first one is often black.
    val thumbnail by produceState<ImageBitmap?>(null, chapter.startMs) {
        value = loadFrame(chapter.startMs + minOf(5_000L, (chapter.endMs - chapter.startMs) / 4))
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(MaterialTheme.shapes.large)
            .background(if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = 112.dp, height = 63.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            thumbnail?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                chapter.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                formatClock(chapter.startMs),
                style = MaterialTheme.typography.labelLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The TV's segment starts and ends as fractions of the runtime, for the split progress bar. */
internal fun NowPlaying.splitFractions(): List<Float> =
    if (durationMs <= 0) emptyList()
    else chapters.drop(1).map { it.startMs.toFloat() / durationMs }.filter { it > 0f && it < 1f }

internal fun List<CompanionChapter>.chapterAt(positionMs: Long): CompanionChapter? =
    lastOrNull { positionMs >= it.startMs } ?: firstOrNull()

