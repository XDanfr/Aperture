@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.graphics.Bitmap
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.text.CueGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.subtitles.OpenSubtitlesSessionState
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.ui.component.expressive.ExpressiveLoadingIndicator
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    mediaId: Long,
    startFromBeginning: Boolean = false,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit = {},
    onLeavePlayerToOpenSubtitles: () -> Unit = {}
) {
    val media by viewModel.media.collectAsState()
    val isOsdVisible by viewModel.isOsdVisible.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()
    val classicPlayerControls by viewModel.classicPlayerControls.collectAsState()
    val onlineSubtitles by viewModel.onlineSubtitles.collectAsState()
    val openSubtitlesSession by viewModel.openSubtitlesSession.collectAsState()
    val compatibilityWarning by viewModel.compatibilityWarning.collectAsState()
    val playbackFailure by viewModel.playbackFailure.collectAsState()
    val subtitleDelayMs by viewModel.subtitleDelayMs.collectAsState()
    val videoDecoderName by viewModel.videoDecoderName.collectAsState()
    val audioDecoderName by viewModel.audioDecoderName.collectAsState()
    val playbackEngine by viewModel.playbackEngine.collectAsState()
    val isCurrentMediaHdr by viewModel.isCurrentMediaHdr.collectAsState()
    val isDisplayHdrCapable by viewModel.isDisplayHdrCapable.collectAsState()
    val player = viewModel.player
    val nativePlayer by player.nativePlayer.collectAsState()
    val hostView = LocalView.current
    var isQuickMenuVisible by remember { mutableStateOf(false) }
    var wasPlayingBeforeQuickMenu by remember { mutableStateOf(false) }
    var videoResizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }
    val playbackState by player.playbackState.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    
    val useGLSurface = remember(playbackEngine, videoDecoderName, isDisplayHdrCapable) {
        (playbackEngine == "compatibility") || 
        (videoDecoderName?.lowercase()?.contains("ffmpeg") == true && !isDisplayHdrCapable)
    }

    val playerFocusRequester = remember { FocusRequester() }
    val controlsFocusRequester = remember { FocusRequester() }
    val quickMenuFocusRequester = remember { FocusRequester() }
    val noticeFocusRequester = remember { FocusRequester() }
    val noticeVisible = compatibilityWarning != null || playbackFailure != null
    var pendingScrubDirection by remember { mutableIntStateOf(0) }

    LaunchedEffect(mediaId) {
        viewModel.loadMedia(mediaId, startFromBeginning)
    }

    DisposableEffect(player) {
        val listener = object : PlayerEngine.Listener {
            override fun onPlaybackStateChanged(state: Int) {}
            override fun onIsPlayingChanged(isPlaying: Boolean) {}
            override fun onPlayerError(error: Throwable) {}
            override fun onPositionDiscontinuity(reason: Int) {}
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        val wasKeepingScreenOn = hostView.keepScreenOn
        hostView.keepScreenOn = true
        onDispose {
            hostView.keepScreenOn = wasKeepingScreenOn
            player.stop()
        }
    }

    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    DisposableEffect(player) {
        var hasReturned = false
        val listener = object : PlayerEngine.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_ENDED && !hasReturned) {
                    hasReturned = true
                    viewModel.saveProgressNow(markCompleted = true)
                    onFinished()
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {}
            override fun onPlayerError(error: Throwable) {}
            override fun onPositionDiscontinuity(reason: Int) {}
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(isOsdVisible, isQuickMenuVisible, noticeVisible) {
        if (noticeVisible) {
            noticeFocusRequester.requestFocus()
        } else if (isOsdVisible && !isQuickMenuVisible) {
            controlsFocusRequester.requestFocus()
        } else if (isQuickMenuVisible) {
            quickMenuFocusRequester.requestFocus()
        } else {
            playerFocusRequester.requestFocus()
        }
    }

    fun saveProgressAndBack() {
        if (isPlaying) player.pause()
        viewModel.saveProgressNow()
        onBack()
    }

    BackHandler {
        when {
            compatibilityWarning != null -> {
                viewModel.dismissCompatibilityWarning()
                onBack()
            }
            playbackFailure != null -> {
                viewModel.dismissPlaybackFailure()
                onBack()
            }
            isQuickMenuVisible -> {
                isQuickMenuVisible = false
                viewModel.hideOsd()
                if (wasPlayingBeforeQuickMenu) player.play()
                wasPlayingBeforeQuickMenu = false
            }
            isOsdVisible -> viewModel.hideOsd()
            else -> {
                if (isPlaying) player.pause()
                viewModel.saveProgressNow()
                onBack()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                if (noticeVisible) false else if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (isOsdVisible && !isQuickMenuVisible && keyEvent.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK) {
                        viewModel.showOsdBriefly()
                    }
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (!isOsdVisible && !isQuickMenuVisible) {
                                if (isPlaying) player.pause() else player.play()
                                viewModel.showOsdBriefly()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!isQuickMenuVisible && !isOsdVisible) {
                                pendingScrubDirection = -1
                                viewModel.showOsdBriefly()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!isQuickMenuVisible && !isOsdVisible) {
                                pendingScrubDirection = 1
                                viewModel.showOsdBriefly()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!isQuickMenuVisible && !isOsdVisible) {
                                viewModel.showOsdBriefly()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_BACK -> false
                        else -> false
                    }
                } else false
            }
            .focusRequester(playerFocusRequester)
            .focusable()
    ) {
    key(useGLSurface) {
        AndroidView(
            factory = { context ->
                val themedContext = if (useGLSurface) {
                    ContextThemeWrapper(context, me.xdan.aperture.R.style.PlayerViewGL)
                } else {
                    context
                }
                Log.d("PlayerScreen", "Creating PlayerView, initial nativePlayer: ${nativePlayer != null}, useGLSurface: $useGLSurface")
                PlayerView(themedContext).apply {
                    useController = false
                    subtitleView?.visibility = View.GONE
                    this.player = nativePlayer
                }
            },
            update = { view ->
                if (view.player != nativePlayer) {
                    Log.d("PlayerScreen", "Updating PlayerView player: ${nativePlayer != null}")
                    view.player = nativePlayer
                }
                view.useController = false
                view.subtitleView?.visibility = View.GONE
                view.resizeMode = videoResizeMode.media3Mode
            },
            modifier = Modifier.fillMaxSize()
        )
    }

        SubtitleOverlay(player = player, style = subtitleStyle)

        AnimatedVisibility(
            visible = media != null && playbackState != androidx.media3.common.Player.STATE_READY && playbackState != androidx.media3.common.Player.STATE_ENDED,
            enter = fadeIn(),
            exit = fadeOut()
        ) { BufferingOverlay(media = media) }

        AnimatedVisibility(
            visible = isOsdVisible && !isQuickMenuVisible,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(220))
        ) {
            if (classicPlayerControls) {
                ClassicPlayerOsd(
                    media = media, mediaSource = media?.filePath, player = player,
                    isVisible = isOsdVisible && !isQuickMenuVisible,
                    videoDecoderName = videoDecoderName,
                    audioDecoderName = audioDecoderName,
                    isHdr = isCurrentMediaHdr,
                    controlsFocusRequester = controlsFocusRequester,
                    onInteraction = viewModel::showOsdBriefly,
                    onRestart = { player.seekTo(0); viewModel.saveProgressNow(); player.play(); viewModel.showOsdBriefly() },
                    onQuickMenu = { wasPlayingBeforeQuickMenu = isPlaying; player.pause(); isQuickMenuVisible = true; viewModel.hideOsd() },
                    onScrubbingChanged = viewModel::setScrubbing,
                    onCloseOsd = viewModel::hideOsd,
                    onPlayerBack = ::saveProgressAndBack,
                    initialScrubDirection = pendingScrubDirection,
                    onInitialScrubConsumed = { pendingScrubDirection = 0 }
                )
            } else {
                ThinPlayerOsd(
                    media = media, mediaSource = media?.filePath, player = player,
                    isVisible = isOsdVisible && !isQuickMenuVisible,
                    videoDecoderName = videoDecoderName,
                    audioDecoderName = audioDecoderName,
                    isHdr = isCurrentMediaHdr,
                    controlsFocusRequester = controlsFocusRequester,
                    onInteraction = viewModel::showOsdBriefly,
                    onRestart = { player.seekTo(0); viewModel.saveProgressNow(); player.play(); viewModel.showOsdBriefly() },
                    onQuickMenu = { wasPlayingBeforeQuickMenu = isPlaying; player.pause(); isQuickMenuVisible = true; viewModel.hideOsd() },
                    onScrubbingChanged = viewModel::setScrubbing,
                    onCloseOsd = viewModel::hideOsd,
                    onPlayerBack = ::saveProgressAndBack,
                    initialScrubDirection = pendingScrubDirection,
                    onInitialScrubConsumed = { pendingScrubDirection = 0 }
                )
            }
        }

        AnimatedVisibility(
            visible = isQuickMenuVisible,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            QuickMenuPages(
                player = player,
                settingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                focusRequester = quickMenuFocusRequester,
                subtitleDelayMs = subtitleDelayMs,
                onSubtitleDelayDecrease = { viewModel.adjustSubtitleDelay(-PlayerViewModel.SYNC_STEP_MS) },
                onSubtitleDelayIncrease = { viewModel.adjustSubtitleDelay(PlayerViewModel.SYNC_STEP_MS) },
                onSubtitleDelayReset = viewModel::resetSubtitleDelay,
                videoResizeMode = videoResizeMode.media3Mode,
                onVideoResizeModeSelected = { mode -> videoResizeMode = VideoResizeMode.entries.first { it.media3Mode == mode } },
                onClose = {
                    isQuickMenuVisible = false
                    viewModel.hideOsd()
                    if (wasPlayingBeforeQuickMenu) player.play()
                    wasPlayingBeforeQuickMenu = false
                },
                onLeavePlayerToOpenSubtitles = onLeavePlayerToOpenSubtitles
            )
        }

        compatibilityWarning?.let { warning ->
            PlaybackNotice(
                title = warning.title, message = warning.message, safeLabel = "Go Back", proceedLabel = warning.proceedLabel,
                extraLabel = "Don't show again",
                safeFocusRequester = noticeFocusRequester,
                onSafe = { viewModel.dismissCompatibilityWarning(); onBack() },
                onProceed = { viewModel.playDespiteWarning() },
                onExtra = { viewModel.playDespiteWarning(dontShowAgain = true) }
            )
        }
        if (compatibilityWarning == null) {
            playbackFailure?.let { failure ->
                PlaybackNotice(
                    title = failure.title, message = failure.message, safeLabel = "Go Back", proceedLabel = "Retry",
                    safeFocusRequester = noticeFocusRequester,
                    onSafe = { viewModel.dismissPlaybackFailure(); onBack() },
                    onProceed = viewModel::retryPlayback
                )
            }
        }
    }
}

@Composable
private fun BufferingOverlay(media: MediaEntity?) {
    val context = LocalContext.current
    val artworkPath = media?.backdropPath ?: media?.posterPath
    val artworkModel = artworkPath?.let { path ->
        ImageRequest.Builder(context).data(TmdbApi.IMAGE_BASE_URL + (if (media?.backdropPath != null) "w1280" else "w780") + path).crossfade(true).build()
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        artworkModel?.let { model ->
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ExpressiveLoadingIndicator(color = MaterialTheme.colorScheme.primary, size = 52.dp)
            Text(text = media?.title ?: "Preparing playback…", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(text = "Buffering…", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun PlaybackNotice(
    title: String, message: String, safeLabel: String, proceedLabel: String,
    extraLabel: String? = null,
    safeFocusRequester: FocusRequester,
    onSafe: () -> Unit, onProceed: () -> Unit,
    onExtra: (() -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)).padding(48.dp), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.widthIn(max = 680.dp), colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)), shape = RoundedCornerShape(32.dp)) {
            Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onSafe, modifier = Modifier.focusRequester(safeFocusRequester)) { Text(safeLabel) }
                    Spacer(Modifier.width(12.dp))
                    if (extraLabel != null && onExtra != null) {
                        OutlinedButton(onClick = onExtra) { Text(extraLabel) }
                        Spacer(Modifier.width(12.dp))
                    }
                    OutlinedButton(onClick = onProceed) { Text(proceedLabel) }
                }
            }
        }
    }
}

@Composable
private fun QuickMenu(player: PlayerEngine, focusRequester: FocusRequester, onlineSubtitleState: OnlineSubtitleState, openSubtitlesSession: OpenSubtitlesSessionState, videoResizeMode: VideoResizeMode, onVideoResizeModeSelected: (VideoResizeMode) -> Unit, subtitleDelayMs: Long, onSubtitleDelayDecrease: () -> Unit, onSubtitleDelayIncrease: () -> Unit, onSubtitleDelayReset: () -> Unit, onSearchOnline: () -> Unit, onDownloadOnline: (OnlineSubtitleOption) -> Unit) {
    val tracks by player.tracks.collectAsState()
    Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.54f).padding(horizontal = 32.dp, vertical = 20.dp), colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)), shape = RoundedCornerShape(32.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            QuickMenuColumn(title = "Audio", icon = Icons.Rounded.Audiotrack, items = getTrackItems(tracks, C.TRACK_TYPE_AUDIO).filter { it.isSupported }, emptyLabel = "No compatible audio tracks", onItemSelected = { trackGroup, index ->
                if (trackGroup.isTrackSupported(index)) player.setTrackSelectionOverride(trackGroup.mediaTrackGroup, index)
            })
            QuickMenuColumn(title = "Subtitles", icon = Icons.Rounded.Subtitles, items = getTrackItems(tracks, C.TRACK_TYPE_TEXT).filter { it.isSupported }, emptyLabel = "No compatible subtitle tracks", headerContent = {
                TimingAdjustmentControl(label = "Subtitle sync", valueMs = subtitleDelayMs, supportingText = "Negative values show subs earlier", focusRequester = focusRequester, onDecrease = onSubtitleDelayDecrease, onIncrease = onSubtitleDelayIncrease, onReset = onSubtitleDelayReset)
            }, onItemSelected = { trackGroup, index ->
                if (trackGroup.isTrackSupported(index)) {
                    player.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    player.setTrackSelectionOverride(trackGroup.mediaTrackGroup, index)
                }
            }, onDisable = {
                player.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                player.clearTrackOverrides(C.TRACK_TYPE_TEXT)
            }, disableLabel = "Off")
            OnlineSubtitlesColumn(state = onlineSubtitleState, session = openSubtitlesSession, onSearch = onSearchOnline, onDownload = onDownloadOnline)
            PlaybackOptionsColumn(selectedResizeMode = videoResizeMode, onResizeModeSelected = onVideoResizeModeSelected)
        }
    }
}

private enum class VideoResizeMode(val label: String, val description: String, val media3Mode: Int) {
    FIT("Fit", "Show the complete picture", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Stretch", "Fill the screen without preserving shape", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", "Fill the screen and crop the edges", AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
}

@Composable
private fun RowScope.PlaybackOptionsColumn(selectedResizeMode: VideoResizeMode, onResizeModeSelected: (VideoResizeMode) -> Unit) {
    Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AspectRatio, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Picture", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VideoResizeMode.entries) { resizeMode ->
                Surface(onClick = { onResizeModeSelected(resizeMode) }, modifier = Modifier.fillMaxWidth(), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = if (resizeMode == selectedResizeMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(resizeMode.label, style = MaterialTheme.typography.bodyMedium)
                        Text(resizeMode.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

data class TrackItem(val name: String, val isSelected: Boolean, val isSupported: Boolean, val group: Tracks.Group, val index: Int)

@Composable
private fun RowScope.QuickMenuColumn(title: String, icon: ImageVector, items: List<Any>, onItemSelected: (Tracks.Group, Int) -> Unit, emptyLabel: String = "No tracks found", headerContent: (@Composable () -> Unit)? = null, onDisable: (() -> Unit)? = null, disableLabel: String? = null) {
    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        headerContent?.let { Spacer(Modifier.height(12.dp)); it() }
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (items.isEmpty()) item { Text(emptyLabel, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            if (onDisable != null && disableLabel != null) item {
                Surface(onClick = onDisable, modifier = Modifier.fillMaxWidth(), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp))) { Text(disableLabel, modifier = Modifier.padding(8.dp)) }
            }
            items(items) { item ->
                val label = if (item is TrackItem) item.name else item.toString()
                val isSelected = if (item is TrackItem) item.isSelected else false
                Surface(onClick = { if (item is TrackItem) onItemSelected(item.group, item.index) }, modifier = Modifier.fillMaxWidth(), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                    Text(label, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TimingAdjustmentControl(label: String, valueMs: Long, supportingText: String, focusRequester: FocusRequester? = null, onDecrease: () -> Unit, onIncrease: () -> Unit, onReset: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TimingButton(label = "−", onClick = onDecrease, modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            TimingButton(label = formatDelay(valueMs), onClick = onReset, modifier = Modifier.weight(1f))
            TimingButton(label = "+", onClick = onIncrease)
        }
        Text(supportingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun TimingButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.height(38.dp), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(19.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.primary, focusedContentColor = MaterialTheme.colorScheme.onPrimary)) {
        Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

private fun formatDelay(delayMs: Long): String = when {
    delayMs == 0L -> "0 ms"
    kotlin.math.abs(delayMs) < 1_000L -> String.format(Locale.getDefault(), "%+d ms", delayMs)
    else -> String.format(Locale.getDefault(), "%+.1f s", delayMs / 1_000f)
}

@Composable
private fun RowScope.OnlineSubtitlesColumn(state: OnlineSubtitleState, session: OpenSubtitlesSessionState, onSearch: () -> Unit, onDownload: (OnlineSubtitleOption) -> Unit) {
    Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("OpenSubtitles", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        if (session !is OpenSubtitlesSessionState.SignedIn) {
            Text("Sign in from Settings", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            Surface(onClick = onSearch, enabled = state !is OnlineSubtitleState.Loading && state !is OnlineSubtitleState.Downloading, modifier = Modifier.fillMaxWidth(), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp))) {
                Text(if (state is OnlineSubtitleState.Loading) "Searching…" else "Search online", modifier = Modifier.padding(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is OnlineSubtitleState.Results -> {
                        if (state.options.isEmpty()) item { Text("No subtitles found", style = MaterialTheme.typography.bodySmall) }
                        items(state.options, key = { it.fileId }) { option ->
                            Surface(onClick = { onDownload(option) }, modifier = Modifier.fillMaxWidth(), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp))) { Text(option.label, modifier = Modifier.padding(8.dp)) }
                        }
                    }
                    is OnlineSubtitleState.Downloading -> item { Text("Downloading ${state.label}…", style = MaterialTheme.typography.bodySmall) }
                    is OnlineSubtitleState.Attached -> item { Text("Attached ${state.label}", style = MaterialTheme.typography.bodySmall) }
                    is OnlineSubtitleState.Error -> item { Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun SubtitleOverlay(player: PlayerEngine, style: PlayerSubtitleStyle) {
    val cues by player.cues.collectAsState()
    val textColour = when (style.colour) { "yellow" -> android.graphics.Color.YELLOW; "cyan" -> android.graphics.Color.CYAN; else -> android.graphics.Color.WHITE }
    val backgroundColour = android.graphics.Color.argb((style.backgroundOpacity.coerceIn(0f, 0.9f) * 255).toInt(), 12, 12, 14)
    AndroidView(factory = { context -> SubtitleView(context).apply { setApplyEmbeddedStyles(false) } }, update = { view ->
        view.setCues(cues.cues)
        view.setFractionalTextSize(0.0533f * style.textScale)
        view.setStyle(CaptionStyleCompat(textColour, android.graphics.Color.TRANSPARENT, backgroundColour, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, android.graphics.Color.BLACK, android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)))
    }, modifier = Modifier.fillMaxSize())
}

private fun getTrackItems(tracks: Tracks, type: Int): List<TrackItem> {
    val items = mutableListOf<TrackItem>()
    tracks.groups.forEach { group ->
        if (group.type == type) for (i in 0 until group.length) {
            items.add(TrackItem(name = group.getTrackFormat(i).let { format -> format.label ?: format.language?.uppercase() ?: when (type) { C.TRACK_TYPE_AUDIO -> "Audio ${i + 1}"; C.TRACK_TYPE_TEXT -> "Subtitle ${i + 1}"; else -> "Track ${i + 1}" } }, isSelected = group.isTrackSelected(i), isSupported = group.isTrackSupported(i), group = group, index = i))
        }
    }
    return items
}

@Composable
private fun ThinPlayerOsd(
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
    var playFocused by remember { mutableStateOf(false) }
    val topFocusRequester = remember { FocusRequester() }
    var originalPosition by remember { mutableLongStateOf(player.currentPosition) }
    var seekPosition by remember { mutableLongStateOf(player.currentPosition) }
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    var holdDirection by remember { mutableIntStateOf(0) }
    var seekJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val uiAlpha by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isScrubbing) 0f else 1f, animationSpec = androidx.compose.animation.core.tween(180), label = "thinOsdUiAlpha")
    val backgroundAlpha by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isScrubbing) 0.76f else 0.52f, animationSpec = androidx.compose.animation.core.tween(220), label = "thinOsdBackgroundAlpha")
    val entryAlpha by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isVisible) 1f else 0f, animationSpec = androidx.compose.animation.core.tween(150), label = "thinOsdEntryAlpha")
    val entryScale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isVisible) 1f else 0.92f, animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow, dampingRatio = 0.72f), label = "thinOsdEntryScale")
    
    LaunchedEffect(player) {
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            currentPosition = player.currentPosition; duration = player.duration; kotlinx.coroutines.delay(33)
        }
    }
    fun endHold() { holdDirection = 0; seekJob?.cancel(); seekJob = null }
    fun beginScrubbing(direction: Int, startHold: Boolean = true) {
        if (!isScrubbing) {
            originalPosition = player.currentPosition; seekPosition = originalPosition; wasPlayingBeforeScrub = isPlaying; isScrubbing = true; onScrubbingChanged(true); player.pause()
        }
        seekPosition = if (direction < 0) (seekPosition - 10_000L).coerceAtLeast(0L) else (seekPosition + 10_000L).coerceAtMost(duration)
        if (!startHold) return
        if (holdDirection == direction && seekJob?.isActive == true) return
        holdDirection = direction; seekJob?.cancel()
        seekJob = scope.launch {
            val startedAt = android.os.SystemClock.elapsedRealtime(); var lastStepAt = startedAt
            seekPosition = if (direction < 0) (seekPosition - 10_000L).coerceAtLeast(0L) else (seekPosition + 10_000L).coerceAtMost(duration)
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val now = android.os.SystemClock.elapsedRealtime(); val elapsed = now - startedAt
                val interval: Long; val step: Long
                when {
                    elapsed < 1_000L -> { interval = 250L; step = 10_000L }
                    elapsed < 2_500L -> { interval = 220L; step = 20_000L }
                    elapsed < 5_000L -> { interval = 180L; step = 30_000L }
                    elapsed < 8_000L -> { interval = 140L; step = 60_000L }
                    else -> { interval = 110L; step = 120_000L }
                }
                if (now - lastStepAt >= interval) { seekPosition = if (direction < 0) (seekPosition - step).coerceAtLeast(0L) else (seekPosition + step).coerceAtMost(duration); lastStepAt = now }
                kotlinx.coroutines.delay(16L)
            }
        }
    }
    LaunchedEffect(initialScrubDirection) {
        if (initialScrubDirection != 0) { beginScrubbing(initialScrubDirection, startHold = false); onInitialScrubConsumed() }
    }
    fun commitScrubbing() {
        if (!isScrubbing) return
        endHold(); player.seekTo(seekPosition.coerceIn(0L, duration)); isScrubbing = false; onScrubbingChanged(false); if (wasPlayingBeforeScrub) player.play()
    }
    fun cancelScrubbing(closeOsd: Boolean = false) {
        if (!isScrubbing) return
        endHold(); player.seekTo(originalPosition.coerceIn(0L, duration)); isScrubbing = false; onScrubbingChanged(false); if (wasPlayingBeforeScrub) player.play(); controlsFocusRequester.requestFocus(); if (closeOsd) onCloseOsd()
    }
    BackHandler(enabled = isScrubbing) { cancelScrubbing() }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = backgroundAlpha)).padding(48.dp).onPreviewKeyEvent { event ->
        val keyCode = event.nativeKeyEvent.keyCode
        when (event.nativeKeyEvent.action) {
            KeyEvent.ACTION_DOWN -> when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> if (isScrubbing || playFocused) { beginScrubbing(-1); true } else false
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (isScrubbing || playFocused) { beginScrubbing(1); true } else false
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (isScrubbing) { commitScrubbing(); true } else false
                KeyEvent.KEYCODE_DPAD_UP -> if (isScrubbing) { cancelScrubbing(closeOsd = true); true } else if (playFocused) { topFocusRequester.requestFocus(); true } else false
                KeyEvent.KEYCODE_DPAD_DOWN -> if (isScrubbing) { cancelScrubbing(); onQuickMenu(); true } else if (playFocused) { onQuickMenu(); true } else false
                else -> false
            }
            KeyEvent.ACTION_UP -> when (keyCode) { KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> { endHold(); true }; else -> false }
            else -> false
        }
    }) {
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).graphicsLayer {
            alpha = uiAlpha * entryAlpha
            val scale = (0.97f + (0.03f * uiAlpha)) * entryScale
            scaleX = scale; scaleY = scale; translationY = -16f * (1f - uiAlpha)
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerControlIconButton(icon = Icons.Rounded.Replay, contentDescription = "Restart", onClick = onRestart, modifier = Modifier.focusRequester(topFocusRequester))
                Spacer(Modifier.width(8.dp))
                PlayerControlIconButton(icon = Icons.Rounded.ArrowBack, contentDescription = "Close player controls", onClick = onPlayerBack)
            }
            Spacer(Modifier.height(10.dp))
            Text(text = media?.title ?: "", style = MaterialTheme.typography.titleLarge, color = Color.White)
            if (media?.type == "EPISODE") {
                val episodeLabel = buildString {
                    if (media.seasonNumber != null && media.episodeNumber != null) append("S%02d:E%02d".format(media.seasonNumber, media.episodeNumber))
                    if (!media.episodeTitle.isNullOrBlank()) { if (isNotEmpty()) append(" "); append('"'); append(media.episodeTitle); append('"') }
                }
                if (episodeLabel.isNotBlank()) Text(text = episodeLabel, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.82f), modifier = Modifier.padding(top = 4.dp))
            }
            if (videoDecoderName != null || audioDecoderName != null) {
                Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecoderBadge(label = "VID", name = videoDecoderName, isHdr = isHdr)
                    DecoderBadge(label = "AUD", name = audioDecoderName)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.graphicsLayer { alpha = uiAlpha * entryAlpha; val scale = (0.97f + (0.03f * uiAlpha)) * entryScale; scaleX = scale; scaleY = scale }, verticalAlignment = Alignment.CenterVertically) {
                PlayerControlIconButton(icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", iconSize = 56.dp, onClick = { if (isPlaying) player.pause() else player.play(); onInteraction() }, modifier = Modifier.focusRequester(controlsFocusRequester).onFocusChanged { playFocused = it.isFocused })
            }
            Spacer(Modifier.width(12.dp))
            Text(text = formatTime(currentPosition), style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.graphicsLayer { alpha = uiAlpha })
            Spacer(Modifier.width(10.dp))
            PlayerSeekProgress(player = player, mediaSource = mediaSource, progress = if (duration > 0L) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f, isPlaying = isPlaying, scrubbing = isScrubbing, seekPosition = seekPosition, modifier = Modifier.weight(1f).height(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(text = formatTime(duration), style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.graphicsLayer { alpha = uiAlpha })
        }
        if (!isScrubbing) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Open Quick Menu",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(56.dp)
                    .offset(y = 32.dp),
                tint = Color.White.copy(alpha = 0.62f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerSeekProgress(player: PlayerEngine, mediaSource: String?, progress: Float, isPlaying: Boolean, scrubbing: Boolean, seekPosition: Long, modifier: Modifier = Modifier) {
    val duration = player.duration.coerceAtLeast(0L)
    val context = LocalContext.current
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val previewLoader = remember(context) { PreviewFrameLoader(context) }
    val targetProgress = if (scrubbing && duration > 0L) (seekPosition.toFloat() / duration).coerceIn(0f, 1f) else progress.coerceIn(0f, 1f)
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(targetValue = targetProgress, animationSpec = androidx.compose.animation.core.tween(80), label = "thinSeekProgress")
    val waveAmplitude = if (isPlaying && !scrubbing && progress >= 0.05f) 1f else 0f
    val previewPosition = if (scrubbing) PreviewFrameLoader.quantise(seekPosition) else -1L
    LaunchedEffect(previewPosition, scrubbing, mediaSource) {
        previewBitmap = null
        if (!scrubbing || mediaSource.isNullOrBlank() || previewPosition < 0L) return@LaunchedEffect
        delay(100); previewBitmap = previewLoader.load(mediaSource, previewPosition)
    }
    DisposableEffect(previewLoader) { onDispose { previewLoader.clear() } }
    BoxWithConstraints(modifier = modifier.layout { measurable, constraints ->
        val relaxed = constraints.copy(maxHeight = maxOf(constraints.maxHeight, 240.dp.roundToPx()))
        val placeable = measurable.measure(relaxed)
        val reportedHeight = 24.dp.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, reportedHeight) { placeable.placeRelative(0, (reportedHeight - placeable.height) / 2) }
    }) {
        if (scrubbing) {
            val previewWidth = 320.dp; val previewHeight = 180.dp
            val previewX = ((maxWidth - previewWidth) * animatedProgress).coerceIn(0.dp, (maxWidth - previewWidth).coerceAtLeast(0.dp))
            Surface(modifier = Modifier.width(previewWidth).wrapContentHeight().offset(x = previewX, y = -(previewHeight + 28.dp) / 2).align(Alignment.CenterStart), shape = RoundedCornerShape(24.dp), colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)), tonalElevation = 6.dp) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(previewHeight).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        if (previewBitmap != null) Image(bitmap = previewBitmap!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else ExpressiveLoadingIndicator(color = MaterialTheme.colorScheme.primary, size = 28.dp)
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(text = formatTime(seekPosition), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        LinearWavyProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().align(Alignment.Center), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant, trackStroke = WavyProgressIndicatorDefaults.linearTrackStroke, stopSize = 0.dp, amplitude = { waveAmplitude }, wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength)
    }
}

@Composable
private fun PlayerControlIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, iconSize: Dp = 48.dp) {
    IconButton(onClick = onClick, modifier = modifier.then(Modifier.clip(CircleShape)), colors = IconButtonDefaults.colors(containerColor = Color.Transparent, contentColor = Color.White, focusedContainerColor = MaterialTheme.colorScheme.primary, focusedContentColor = MaterialTheme.colorScheme.onPrimary, pressedContainerColor = MaterialTheme.colorScheme.primary, pressedContentColor = MaterialTheme.colorScheme.onPrimary), scale = IconButtonDefaults.scale(scale = 1f, focusedScale = 1.12f, pressedScale = 0.84f)) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
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

private fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
