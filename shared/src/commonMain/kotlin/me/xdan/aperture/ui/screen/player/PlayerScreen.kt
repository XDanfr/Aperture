package me.xdan.aperture.ui.screen.player

import org.koin.compose.viewmodel.koinViewModel

import me.xdan.aperture.platform.Log
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import me.xdan.aperture.ui.platform.isBackKey
import me.xdan.aperture.ui.platform.isCanceledCompat
import me.xdan.aperture.ui.platform.repeatCountCompat
import me.xdan.aperture.ui.platform.BackHandler
import me.xdan.aperture.ui.platform.rememberBackDispatcher
import me.xdan.aperture.ui.platform.LocalToggleFullscreen
import me.xdan.aperture.ui.platform.hiddenPointerIcon
import me.xdan.aperture.ui.platform.onBackgroundClick
import me.xdan.aperture.ui.platform.onMouseMove
import me.xdan.aperture.ui.platform.usesMouse
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.subtitles.OpenSubtitlesSessionState
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.ui.component.expressive.ExpressiveLoadingIndicator
import me.xdan.aperture.ui.theme.ApertureTheme
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    mediaId: Long,
    startFromBeginning: Boolean = false,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit = {},
    onPlaybackMediaChanged: (Long) -> Unit = {},
    onLeavePlayerToOpenSubtitles: () -> Unit = {}
) {
    val media by viewModel.media.collectAsState()
    val nextEpisode by viewModel.nextEpisode.collectAsState()
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
    val nativePlayer by player.videoOutput.collectAsState()
    val windowInfo = LocalWindowInfo.current
    val backDispatcher = rememberBackDispatcher()
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
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    var nextEpisodeDismissed by remember(media?.id) { mutableStateOf(false) }
    var remainingMillis by remember(media?.id) { mutableLongStateOf(Long.MAX_VALUE) }
    var advancingEpisode by remember { mutableStateOf(false) }
    var entryMediaLoaded by remember(mediaId) { mutableStateOf(false) }
    val currentOnPlaybackMediaChanged by rememberUpdatedState(onPlaybackMediaChanged)
    LaunchedEffect(media?.id) {
        advancingEpisode = false
        val loadedId = media?.id
        if (loadedId == mediaId) entryMediaLoaded = true
        if (entryMediaLoaded && loadedId != null) currentOnPlaybackMediaChanged(loadedId)
    }
    LaunchedEffect(player, media?.id) {
        while (isActive) {
            remainingMillis = player.duration.takeIf { it > 0L }
                ?.let { (it - player.currentPosition).coerceAtLeast(0L) } ?: Long.MAX_VALUE
            delay(250)
        }
    }
    val noticeVisible = compatibilityWarning != null || playbackFailure != null
    val nextEpisodeVisible = nextEpisode != null && !nextEpisodeDismissed && !advancingEpisode &&
        remainingMillis <= 10_000L && playbackState == PlaybackStates.READY &&
        !isQuickMenuVisible && !noticeVisible
    fun advanceEpisode() {
        if (advancingEpisode || nextEpisode == null) return
        advancingEpisode = true
        viewModel.playNextEpisode()
    }
    val handlePlaybackEnded by rememberUpdatedState<() -> Unit> {
        if (!advancingEpisode) {
            if (nextEpisode != null && !nextEpisodeDismissed) advanceEpisode()
            else {
                viewModel.saveProgressNow(markCompleted = true)
                onFinished()
            }
        }
    }
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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    me.xdan.aperture.ui.platform.KeepScreenOn(isPlaying)
    DisposableEffect(player, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                player.pause()
                viewModel.saveProgressNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.stop()
        }
    }

    DisposableEffect(player) {
        var hasHandledEnd = false
        val listener = object : PlayerEngine.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == PlaybackStates.READY) hasHandledEnd = false
                if (state == PlaybackStates.ENDED && !hasHandledEnd) {
                    hasHandledEnd = true
                    handlePlaybackEnded()
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {}
            override fun onPlayerError(error: Throwable) {}
            override fun onPositionDiscontinuity(reason: Int) {}
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(nextEpisodeVisible) {
        if (nextEpisodeVisible) {
            viewModel.hideOsd()
        }
    }

    val desiredFocusRequester = when {
        noticeVisible -> noticeFocusRequester
        nextEpisodeVisible -> nextEpisodeFocusRequester
        isQuickMenuVisible -> quickMenuFocusRequester
        isOsdVisible -> controlsFocusRequester
        else -> playerFocusRequester
    }
    LaunchedEffect(desiredFocusRequester, windowInfo.isWindowFocused) {
        if (!windowInfo.isWindowFocused) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        repeat(10) {
            if (runCatching { desiredFocusRequester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(50)
        }
    }

    fun closeQuickMenu() {
        isQuickMenuVisible = false
        viewModel.hideOsd()
        if (wasPlayingBeforeQuickMenu) player.play()
        wasPlayingBeforeQuickMenu = false
    }

    fun togglePlayback() {
        if (isPlaying) player.pause() else player.play()
        viewModel.showOsdBriefly()
    }

    val toggleFullscreen = LocalToggleFullscreen.current

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
            isQuickMenuVisible -> closeQuickMenu()
            nextEpisodeVisible -> nextEpisodeDismissed = true
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
            // Desktop: moving the mouse brings the controls back, as in any desktop player, and
            // the cursor hides with them while video plays.
            .onMouseMove(enabled = !noticeVisible && !nextEpisodeVisible && !isQuickMenuVisible) {
                viewModel.showOsdBriefly()
            }
            .then(
                if (usesMouse && isPlaying && !isOsdVisible && !isQuickMenuVisible && !noticeVisible) {
                    Modifier.pointerHoverIcon(hiddenPointerIcon(), overrideDescendants = true)
                } else Modifier
            )
            // Desktop: a click on the video plays or pauses (or closes the quick menu, like any
            // popup), and a double click toggles fullscreen. Controls take their own clicks first.
            .onBackgroundClick(
                enabled = !noticeVisible,
                onDoubleClick = toggleFullscreen?.let { toggle -> { togglePlayback(); toggle() } }
            ) {
                when {
                    isQuickMenuVisible -> closeQuickMenu()
                    nextEpisodeVisible -> Unit
                    else -> togglePlayback()
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.isBackKey() && backDispatcher != null) {
                    // Consume both halves before focused TV controls can clear focus.
                    // Dispatch on release so nested BackHandlers handle exactly one step.
                    if (keyEvent.type == KeyEventType.KeyUp && !keyEvent.isCanceledCompat) {
                        backDispatcher()
                    }
                    true
                } else if (noticeVisible || nextEpisodeVisible) false else if (keyEvent.type == KeyEventType.KeyDown) {
                    if (isOsdVisible && !isQuickMenuVisible && !keyEvent.isBackKey()) {
                        viewModel.showOsdBriefly()
                    }
                    when (keyEvent.key) {
                        Key.MediaPlayPause -> {
                            if (keyEvent.repeatCountCompat == 0) {
                                if (isPlaying) player.pause() else player.play()
                            }
                            true
                        }
                        Key.MediaPlay -> { player.play(); true }
                        // Desktop keyboards: Space plays and pauses wherever focus is.
                        Key.Spacebar -> {
                            if (usesMouse && !isQuickMenuVisible) {
                                if (keyEvent.repeatCountCompat == 0) togglePlayback()
                                true
                            } else false
                        }
                        Key.MediaPause, Key.MediaStop -> { player.pause(); true }

                        Key.DirectionCenter, Key.Enter -> {
                            if (!isOsdVisible && !isQuickMenuVisible) {
                                if (isPlaying) player.pause() else player.play()
                                viewModel.showOsdBriefly()
                                true
                            } else false
                        }
                        Key.DirectionLeft -> {
                            if (!isQuickMenuVisible && !isOsdVisible) {
                                pendingScrubDirection = -1
                                viewModel.showOsdBriefly()
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (!isQuickMenuVisible && !isOsdVisible) {
                                pendingScrubDirection = 1
                                viewModel.showOsdBriefly()
                                true
                            } else false
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (!isQuickMenuVisible && !isOsdVisible) {
                                viewModel.showOsdBriefly()
                                true
                            } else false
                        }
                        Key.Back -> false
                        else -> false
                    }
                } else false
            }
            .focusRequester(playerFocusRequester)
            .focusable()
    ) {
        PlayerVideoSurface(
            output = nativePlayer,
            useGLSurface = useGLSurface,
            resizeMode = videoResizeMode.media3Mode,
            modifier = Modifier.fillMaxSize()
        )

        SubtitleOverlay(player = player, style = subtitleStyle)

        AnimatedVisibility(
            visible = media != null && playbackState != PlaybackStates.READY && playbackState != PlaybackStates.ENDED,
            enter = fadeIn(),
            exit = fadeOut()
        ) { BufferingOverlay(media = media) }

        AnimatedVisibility(
            visible = isOsdVisible && !isQuickMenuVisible && !nextEpisodeVisible,
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
                settingsViewModel = org.koin.compose.viewmodel.koinViewModel(),
                focusRequester = quickMenuFocusRequester,
                subtitleDelayMs = subtitleDelayMs,
                onSubtitleDelayDecrease = { viewModel.adjustSubtitleDelay(-PlayerViewModel.SYNC_STEP_MS) },
                onSubtitleDelayIncrease = { viewModel.adjustSubtitleDelay(PlayerViewModel.SYNC_STEP_MS) },
                onSubtitleDelayReset = viewModel::resetSubtitleDelay,
                videoResizeMode = videoResizeMode.media3Mode,
                onVideoResizeModeSelected = { mode -> videoResizeMode = VideoResizeMode.entries.first { it.media3Mode == mode } },
                onClose = ::closeQuickMenu,
                onLeavePlayerToOpenSubtitles = onLeavePlayerToOpenSubtitles
            )
        }

        AnimatedVisibility(
            visible = nextEpisodeVisible,
            enter = fadeIn(ApertureTheme.motion.enter()) + slideInHorizontally(ApertureTheme.motion.enter()) { it / 4 },
            exit = fadeOut(ApertureTheme.motion.exit()),
            modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp)
        ) {
            nextEpisode?.let { episode ->
                NextEpisodePopup(
                    episode = episode,
                    secondsRemaining = ((remainingMillis.coerceAtMost(10_000L) + 999L) / 1000L).toInt().coerceIn(0, 10),
                    focusRequester = nextEpisodeFocusRequester,
                    onPlay = ::advanceEpisode,
                    onDismiss = { nextEpisodeDismissed = true }
                )
            }
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
    val context = LocalPlatformContext.current
    val artworkPath = media?.backdropPath ?: media?.posterPath
    val artworkModel = artworkPath?.let { path ->
        ImageRequest.Builder(context).data(TmdbApi.IMAGE_BASE_URL + (if (media?.backdropPath != null) "w1280" else "w780") + path).crossfade(100).build()
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
            QuickMenuColumn(title = "Audio", icon = Icons.Rounded.Audiotrack, items = getTrackItems(tracks, TrackTypes.AUDIO).filter { it.isSupported }, emptyLabel = "No compatible audio tracks", onItemSelected = { track ->
                if (track.isSupported) player.selectTrack(track)
            })
            QuickMenuColumn(title = "Subtitles", icon = Icons.Rounded.Subtitles, items = getTrackItems(tracks, TrackTypes.TEXT).filter { it.isSupported }, emptyLabel = "No compatible subtitle tracks", headerContent = {
                TimingAdjustmentControl(label = "Subtitle sync", valueMs = subtitleDelayMs, supportingText = "Negative values show subs earlier", focusRequester = focusRequester, onDecrease = onSubtitleDelayDecrease, onIncrease = onSubtitleDelayIncrease, onReset = onSubtitleDelayReset)
            }, onItemSelected = { track ->
                if (track.isSupported) {
                    player.setTrackTypeDisabled(TrackTypes.TEXT, false)
                    player.selectTrack(track)
                }
            }, onDisable = {
                player.setTrackTypeDisabled(TrackTypes.TEXT, true)
                player.clearTrackOverrides(TrackTypes.TEXT)
            }, disableLabel = "Off")
            OnlineSubtitlesColumn(state = onlineSubtitleState, session = openSubtitlesSession, onSearch = onSearchOnline, onDownload = onDownloadOnline)
            PlaybackOptionsColumn(selectedResizeMode = videoResizeMode, onResizeModeSelected = onVideoResizeModeSelected)
        }
    }
}

private enum class VideoResizeMode(val label: String, val description: String, val media3Mode: Int) {
    FIT("Fit", "Show the complete picture", VideoResizeModes.FIT),
    FILL("Stretch", "Fill the screen without preserving shape", VideoResizeModes.FILL),
    ZOOM("Zoom", "Fill the screen and crop the edges", VideoResizeModes.ZOOM)
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

data class TrackItem(val name: String, val isSelected: Boolean, val isSupported: Boolean, val track: PlayerTrack, val index: Int)

@Composable
private fun RowScope.QuickMenuColumn(title: String, icon: ImageVector, items: List<Any>, onItemSelected: (PlayerTrack) -> Unit, emptyLabel: String = "No tracks found", headerContent: (@Composable () -> Unit)? = null, onDisable: (() -> Unit)? = null, disableLabel: String? = null) {
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
                Surface(onClick = { if (item is TrackItem) onItemSelected(item.track) }, modifier = Modifier.fillMaxWidth(), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
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
    PlayerSubtitles(cues = cues, style = style, modifier = Modifier.fillMaxSize())
}

private fun getTrackItems(tracks: PlayerTracks, type: Int): List<TrackItem> {
    val items = mutableListOf<TrackItem>()
    tracks.groups.forEach { group ->
        if (group.type == type) group.tracks.forEachIndexed { i, track ->
            items.add(TrackItem(name = track.label ?: track.language?.uppercase() ?: when (type) { TrackTypes.AUDIO -> "Audio ${i + 1}"; TrackTypes.TEXT -> "Subtitle ${i + 1}"; else -> "Track ${i + 1}" }, isSelected = track.isSelected, isSupported = track.isSupported, track = track, index = i))
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
            val startedAt = me.xdan.aperture.platform.elapsedRealtime(); var lastStepAt = startedAt
            seekPosition = if (direction < 0) (seekPosition - 10_000L).coerceAtLeast(0L) else (seekPosition + 10_000L).coerceAtMost(duration)
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val now = me.xdan.aperture.platform.elapsedRealtime(); val elapsed = now - startedAt
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
    // Desktop mouse scrubbing: the same scrubbing state as the D-pad, positioned directly.
    fun pointerScrubTo(fraction: Float) {
        endHold()
        if (!isScrubbing) {
            originalPosition = player.currentPosition; wasPlayingBeforeScrub = isPlaying; isScrubbing = true; onScrubbingChanged(true); player.pause()
        }
        seekPosition = (fraction * duration).toLong().coerceIn(0L, duration.coerceAtLeast(0L))
        onInteraction()
    }
    BackHandler(enabled = isScrubbing) { cancelScrubbing() }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = backgroundAlpha)).padding(48.dp).onPreviewKeyEvent { event ->
        val keyCode = event.key
        when (event.type) {
            KeyEventType.KeyDown -> when (keyCode) {
                Key.DirectionLeft -> if (isScrubbing || playFocused) { beginScrubbing(-1); true } else false
                Key.DirectionRight -> if (isScrubbing || playFocused) { beginScrubbing(1); true } else false
                Key.DirectionCenter, Key.Enter -> if (isScrubbing) { commitScrubbing(); true } else false
                Key.DirectionUp -> if (isScrubbing) { cancelScrubbing(closeOsd = true); true } else if (playFocused) { topFocusRequester.requestFocus(); true } else false
                Key.DirectionDown -> if (isScrubbing) { cancelScrubbing(); onQuickMenu(); true } else if (playFocused) { onQuickMenu(); true } else false
                else -> false
            }
            KeyEventType.KeyUp -> when (keyCode) { Key.DirectionLeft, Key.DirectionRight -> { endHold(); true }; else -> false }
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
            PlayerSeekProgress(player = player, mediaSource = mediaSource, progress = if (duration > 0L) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f, isPlaying = isPlaying, scrubbing = isScrubbing, seekPosition = seekPosition, onPointerScrub = ::pointerScrubTo, onPointerScrubEnd = { commitScrubbing(); onInteraction() }, onHover = { onInteraction() }, modifier = Modifier.weight(1f).height(24.dp))
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
                    .offset(y = 32.dp)
                    // Desktop: the hint is also a button (on TV, Down opens the menu).
                    .then(if (usesMouse) Modifier.clip(CircleShape).clickable(onClick = onQuickMenu) else Modifier),
                tint = Color.White.copy(alpha = 0.62f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerSeekProgress(player: PlayerEngine, mediaSource: String?, progress: Float, isPlaying: Boolean, scrubbing: Boolean, seekPosition: Long, onPointerScrub: (Float) -> Unit = {}, onPointerScrubEnd: () -> Unit = {}, onHover: () -> Unit = {}, modifier: Modifier = Modifier) {
    val duration = player.duration.coerceAtLeast(0L)
    var previewBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val previewLoader = rememberPreviewFrames()
    // Desktop: where the mouse hovers over the bar, previewed without scrubbing.
    var hoverFraction by remember { mutableStateOf<Float?>(null) }
    val targetProgress = if (scrubbing && duration > 0L) (seekPosition.toFloat() / duration).coerceIn(0f, 1f) else progress.coerceIn(0f, 1f)
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(targetValue = targetProgress, animationSpec = androidx.compose.animation.core.tween(80), label = "thinSeekProgress")
    val wavy = isPlaying && !scrubbing && progress >= 0.05f
    val hoverPosition = if (!scrubbing) hoverFraction?.let { (it * duration).toLong() } else null
    val previewTime = if (scrubbing) seekPosition else hoverPosition
    val previewPosition = previewTime?.let(PreviewFrameLoader::quantise) ?: -1L
    LaunchedEffect(previewPosition, mediaSource) {
        previewBitmap = null
        if (mediaSource.isNullOrBlank() || previewPosition < 0L) return@LaunchedEffect
        delay(100); previewBitmap = previewLoader.load(mediaSource, previewPosition)
    }
    DisposableEffect(previewLoader) { onDispose { previewLoader.clear() } }
    BoxWithConstraints(modifier = modifier.seekBarPointerInput(
        onHover = { fraction -> hoverFraction = fraction; if (fraction != null) onHover() },
        onScrubStart = onPointerScrub,
        onScrub = onPointerScrub,
        onScrubEnd = onPointerScrubEnd
    ).layout { measurable, constraints ->
        val relaxed = constraints.copy(maxHeight = maxOf(constraints.maxHeight, 240.dp.roundToPx()))
        val placeable = measurable.measure(relaxed)
        val reportedHeight = 24.dp.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, reportedHeight) { placeable.placeRelative(0, (reportedHeight - placeable.height) / 2) }
    }) {
        if (previewTime != null) {
            val previewWidth = 320.dp; val previewHeight = 180.dp
            val previewFraction = if (scrubbing) animatedProgress else hoverFraction ?: 0f
            val previewX = ((maxWidth - previewWidth) * previewFraction).coerceIn(0.dp, (maxWidth - previewWidth).coerceAtLeast(0.dp))
            Surface(modifier = Modifier.width(previewWidth).wrapContentHeight().offset(x = previewX, y = -(previewHeight + 28.dp) / 2).align(Alignment.CenterStart), shape = RoundedCornerShape(24.dp), colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)), tonalElevation = 6.dp) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(previewHeight).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        if (previewBitmap != null) Image(bitmap = previewBitmap!!, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else ExpressiveLoadingIndicator(color = MaterialTheme.colorScheme.primary, size = 28.dp)
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(text = formatTime(previewTime), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        PlaybackWaveProgress(
            progress = { animatedProgress },
            wavy = wavy,
            modifier = Modifier.fillMaxWidth().align(Alignment.Center)
        )
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
