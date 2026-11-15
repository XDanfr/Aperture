
package me.xdan.aperture.ui.screen.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import me.xdan.aperture.ui.platform.isBackKey
import me.xdan.aperture.ui.platform.isCanceledCompat
import me.xdan.aperture.ui.platform.repeatCountCompat
import me.xdan.aperture.ui.platform.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import me.xdan.aperture.ui.platform.usesMouse
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.ui.component.expressive.ExpressiveLoadingIndicator

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ClassicPlayerOsd(
    media: MediaEntity?,
    mediaSource: String?,
    player: PlayerEngine,
    isVisible: Boolean,
    videoDecoderName: String?,
    audioDecoderName: String?,
    isHdr: Boolean = false,
    controlsFocusRequester: FocusRequester,
    onInteraction: () -> Unit,
    onRestart: () -> Unit,
    onQuickMenu: () -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    onCloseOsd: () -> Unit,
    onPlayerBack: () -> Unit,
    initialScrubDirection: Int = 0,
    onInitialScrubConsumed: () -> Unit = {}
) {
    val isPlaying by player.isPlaying.collectAsState()
    var currentPosition by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration) }
    var isScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        controlsFocusRequester.requestFocus()
    }

    val scrubUiAlpha by animateFloatAsState(
        targetValue = if (isScrubbing) 0f else 1f,
        animationSpec = tween(220),
        label = "classicScrubUiAlpha"
    )
    val scrubBackgroundAlpha by animateFloatAsState(
        targetValue = if (isScrubbing) 0.76f else 0.5f,
        animationSpec = tween(220),
        label = "classicScrubBackgroundAlpha"
    )
    val entryAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(150),
        label = "classicOsdEntryAlpha"
    )
    val entryScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.92f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = 0.72f
        ),
        label = "classicOsdEntryScale"
    )

    LaunchedEffect(player) {
        while (currentCoroutineContext().isActive) {
            currentPosition = player.currentPosition
            duration = player.duration
            delay(33)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrubBackgroundAlpha))
            .padding(48.dp)
    ) {
        // Desktop: an on-screen way out, where a TV remote would press Back.
        if (usesMouse) {
            ClassicPlayerControlIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onPlayerBack,
                modifier = Modifier.align(Alignment.TopStart).graphicsLayer { alpha = scrubUiAlpha * entryAlpha }
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = scrubUiAlpha * entryAlpha
                    val scale = (0.96f + (0.04f * scrubUiAlpha)) * entryScale
                    scaleX = scale
                    scaleY = scale
                    translationY = -20f * (1f - scrubUiAlpha)
                }
            ) {
                Text(
                    text = media?.title ?: "",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                if (media?.type == "EPISODE") {
                    val episodeLabel = buildString {
                        if (media.seasonNumber != null && media.episodeNumber != null) {
                            append("S%02d:E%02d".format(media.seasonNumber, media.episodeNumber))
                        }
                        if (!media.episodeTitle.isNullOrBlank()) {
                            if (isNotEmpty()) append(" ")
                            append('"')
                            append(media.episodeTitle)
                            append('"')
                        }
                    }
                    if (episodeLabel.isNotBlank()) {
                        Text(
                            text = episodeLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                if (videoDecoderName != null || audioDecoderName != null) {
                    Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecoderBadge(label = "VID", name = videoDecoderName, isHdr = isHdr)
                        DecoderBadge(label = "AUD", name = audioDecoderName)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            val progress = if (duration > 0L) {
                (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }

            ClassicPlayerSeekProgress(
                player = player,
                mediaSource = mediaSource,
                progress = progress,
                isPlaying = isPlaying,
                onScrubbingChanged = { scrubbing ->
                    isScrubbing = scrubbing
                    onScrubbingChanged(scrubbing)
                },
                onScrubUp = onCloseOsd,
                onScrubToControls = controlsFocusRequester::requestFocus,
                initialScrubDirection = initialScrubDirection,
                onInitialScrubConsumed = onInitialScrubConsumed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )

            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = scrubUiAlpha * entryAlpha
                    val scale = (0.96f + (0.04f * scrubUiAlpha)) * entryScale
                    scaleX = scale
                    scaleY = scale
                    translationY = 20f * (1f - scrubUiAlpha)
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(classicFormatTime(currentPosition), color = Color.White)
                    Text(classicFormatTime(duration), color = Color.White)
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClassicPlayerControlIconButton(
                        icon = Icons.Rounded.Replay,
                        contentDescription = "Restart",
                        onClick = onRestart
                    )
                    Spacer(Modifier.width(24.dp))
                    ClassicPlayerControlIconButton(
                        icon = Icons.Rounded.FastRewind,
                        contentDescription = "Rewind",
                        onClick = {
                            player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                            onInteraction()
                        }
                    )
                    Spacer(Modifier.width(32.dp))
                    ClassicPlayerControlIconButton(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        onClick = {
                            if (isPlaying) player.pause() else player.play()
                            onInteraction()
                        },
                        modifier = Modifier.focusRequester(controlsFocusRequester),
                        iconSize = 64.dp
                    )
                    Spacer(Modifier.width(32.dp))
                    ClassicPlayerControlIconButton(
                        icon = Icons.Rounded.FastForward,
                        contentDescription = "Fast Forward",
                        onClick = {
                            val safeDuration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                            player.seekTo((player.currentPosition + 10_000L).coerceAtMost(safeDuration))
                            onInteraction()
                        }
                    )
                    Spacer(Modifier.width(32.dp))
                    ClassicPlayerControlIconButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "Audio and subtitle options",
                        onClick = onQuickMenu
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClassicPlayerSeekProgress(
    player: PlayerEngine,
    mediaSource: String?,
    progress: Float,
    isPlaying: Boolean,
    onScrubbingChanged: (Boolean) -> Unit,
    onScrubUp: () -> Unit,
    onScrubToControls: () -> Unit,
    initialScrubDirection: Int = 0,
    onInitialScrubConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val duration = player.duration.coerceAtLeast(0L)
    var focused by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var originalPosition by remember { mutableLongStateOf(player.currentPosition) }
    var seekPosition by remember { mutableLongStateOf(player.currentPosition) }
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val previewLoader = rememberPreviewFrames()
    var holdDirection by remember { mutableIntStateOf(0) }
    var seekJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val scrubFocusRequester = remember { FocusRequester() }

    val targetProgress = if (scrubbing && duration > 0L) {
        (seekPosition.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        progress.coerceIn(0f, 1f)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(80),
        label = "classicSeekProgress"
    )

    val wavy = isPlaying && !scrubbing && progress >= 0.05f
    // Desktop: where the mouse hovers over the bar, previewed without scrubbing.
    var hoverFraction by remember { mutableStateOf<Float?>(null) }
    val previewTime = if (scrubbing) seekPosition else hoverFraction?.let { (it * duration).toLong() }
    val previewPosition = previewTime?.let(PreviewFrameLoader::quantise) ?: -1L

    LaunchedEffect(previewPosition, mediaSource) {
        previewBitmap = null
        if (mediaSource.isNullOrBlank() || previewPosition < 0L) return@LaunchedEffect
        delay(100)
        previewBitmap = previewLoader.load(mediaSource, previewPosition)
    }

    DisposableEffect(previewLoader) {
        onDispose { previewLoader.clear() }
    }

    val handleSize by animateDpAsState(
        targetValue = if (focused || scrubbing) 14.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "classicSeekHandleSize"
    )

    fun beginScrubbing() {
        if (scrubbing) return
        originalPosition = player.currentPosition
        seekPosition = originalPosition
        wasPlayingBeforeScrub = player.isPlaying.value
        scrubbing = true
        onScrubbingChanged(true)
        player.pause()
    }

    fun commitScrubbing() {
        if (!scrubbing) return
        player.seekTo(seekPosition.coerceIn(0L, duration))
        scrubbing = false
        onScrubbingChanged(false)
        if (wasPlayingBeforeScrub) player.play()
    }

    fun cancelScrubbing() {
        if (!scrubbing) return
        seekJob?.cancel()
        seekJob = null
        holdDirection = 0
        player.seekTo(originalPosition.coerceIn(0L, duration))
        scrubbing = false
        onScrubbingChanged(false)
        if (wasPlayingBeforeScrub) player.play()
    }

    BackHandler(enabled = scrubbing) {
        cancelScrubbing()
        onScrubToControls()
    }

    fun beginHold(direction: Int, startHold: Boolean = true) {
        if (holdDirection == direction && seekJob?.isActive == true) return
        beginScrubbing()
        seekPosition = if (direction < 0) {
            (seekPosition - 10_000L).coerceAtLeast(0L)
        } else {
            (seekPosition + 10_000L).coerceAtMost(duration)
        }
        if (!startHold) return
        holdDirection = direction
        seekJob?.cancel()
        seekJob = scope.launch {
            val startedAt = me.xdan.aperture.platform.elapsedRealtime()
            var lastStepAt = startedAt
            seekPosition = if (direction < 0) {
                (seekPosition - 10_000L).coerceAtLeast(0L)
            } else {
                (seekPosition + 10_000L).coerceAtMost(duration)
            }
            while (currentCoroutineContext().isActive) {
                val now = me.xdan.aperture.platform.elapsedRealtime()
                val elapsed = now - startedAt
                val (interval, step) = when {
                    elapsed < 1_000L -> 250L to 10_000L
                    elapsed < 2_500L -> 220L to 20_000L
                    elapsed < 5_000L -> 180L to 30_000L
                    elapsed < 8_000L -> 140L to 60_000L
                    else -> 110L to 120_000L
                }
                if (now - lastStepAt >= interval) {
                    seekPosition = if (direction < 0) {
                        (seekPosition - step).coerceAtLeast(0L)
                    } else {
                        (seekPosition + step).coerceAtMost(duration)
                    }
                    lastStepAt = now
                }
                delay(16L)
            }
        }
    }

    fun endHold() {
        holdDirection = 0
        seekJob?.cancel()
        seekJob = null
    }

    // Desktop mouse scrubbing: the same scrubbing state as the D-pad, positioned directly.
    fun pointerScrubTo(fraction: Float) {
        endHold()
        beginScrubbing()
        seekPosition = (fraction * duration).toLong().coerceIn(0L, duration)
    }

    LaunchedEffect(initialScrubDirection) {
        if (initialScrubDirection != 0) {
            scrubFocusRequester.requestFocus()
            beginHold(initialScrubDirection, startHold = false)
            onInitialScrubConsumed()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .seekBarPointerInput(
                onHover = { hoverFraction = it },
                onScrubStart = { fraction ->
                    runCatching { scrubFocusRequester.requestFocus() }
                    pointerScrubTo(fraction)
                },
                onScrub = ::pointerScrubTo,
                onScrubEnd = ::commitScrubbing
            )
            .focusRequester(scrubFocusRequester)
            .layout { measurable, constraints ->
                val relaxedConstraints = constraints.copy(
                    maxHeight = maxOf(constraints.maxHeight, 240.dp.roundToPx())
                )
                val placeable = measurable.measure(relaxedConstraints)
                val reportedHeight = 24.dp.roundToPx()
                    .coerceIn(constraints.minHeight, constraints.maxHeight)
                layout(constraints.maxWidth, reportedHeight) {
                    placeable.placeRelative(0, (reportedHeight - placeable.height) / 2)
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused && scrubbing) cancelScrubbing()
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                when (event.type) {
                    KeyEventType.KeyDown -> when (event.key) {
                        Key.DirectionLeft -> { beginHold(-1); true }
                        Key.DirectionRight -> { beginHold(1); true }
                        Key.DirectionCenter, Key.Enter -> {
                            endHold()
                            if (scrubbing) commitScrubbing() else if (player.isPlaying.value) player.pause() else player.play()
                            true
                        }
                        Key.DirectionUp -> {
                            if (scrubbing) {
                                endHold()
                                cancelScrubbing()
                                onScrubUp()
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (scrubbing) {
                                endHold()
                                cancelScrubbing()
                                onScrubToControls()
                                true
                            } else false
                        }
                        Key.Back -> false
                        else -> false
                    }
                    KeyEventType.KeyUp -> when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> { endHold(); true }
                        else -> false
                    }
                    else -> false
                }
            }
    ) {
        if (previewTime != null) {
            val previewWidth = 320.dp
            val previewHeight = 180.dp
            val previewFraction = if (scrubbing) animatedProgress else hoverFraction ?: 0f
            val previewX = ((maxWidth - previewWidth) * previewFraction)
                .coerceIn(0.dp, (maxWidth - previewWidth).coerceAtLeast(0.dp))
            Surface(
                modifier = Modifier
                    .width(previewWidth)
                    .wrapContentHeight()
                    .offset(x = previewX, y = -(previewHeight + 28.dp) / 2)
                    .align(Alignment.CenterStart),
                shape = RoundedCornerShape(24.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
                tonalElevation = 6.dp
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap!!,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            ExpressiveLoadingIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                size = 28.dp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = classicFormatTime(previewTime),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        PlaybackWaveProgress(
            progress = { animatedProgress },
            wavy = wavy,
            modifier = Modifier.fillMaxWidth().align(Alignment.Center)
        )

        if (handleSize > 0.dp && maxWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .size(handleSize)
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * animatedProgress - handleSize / 2)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

@Composable
private fun ClassicPlayerControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 48.dp
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            pressedContainerColor = MaterialTheme.colorScheme.primary,
            pressedContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        scale = IconButtonDefaults.scale(
            scale = 1f,
            focusedScale = 1.12f,
            pressedScale = 0.84f
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun DecoderBadge(label: String, name: String?, isHdr: Boolean = false) {
    if (name == null) return
    val isSoftware = name.lowercase().contains("ffmpeg") || name.lowercase().contains("google")
    Surface(
        shape = RoundedCornerShape(8.dp),
        colors = SurfaceDefaults.colors(
            containerColor = if (isSoftware) Color(0xFFE57373).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.14f)
        )
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = if (isSoftware) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp))
            Text(text = if (isHdr) "$name (HDR)" else name, style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

private fun classicFormatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
