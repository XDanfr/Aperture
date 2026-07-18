@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.view.KeyEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.subtitles.OpenSubtitlesSessionState
import me.xdan.aperture.data.remote.api.TmdbApi
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    mediaId: Long,
    startFromBeginning: Boolean = false,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit = {}
) {
    val media by viewModel.media.collectAsState()
    val isOsdVisible by viewModel.isOsdVisible.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()
    val onlineSubtitles by viewModel.onlineSubtitles.collectAsState()
    val openSubtitlesSession by viewModel.openSubtitlesSession.collectAsState()
    val compatibilityWarning by viewModel.compatibilityWarning.collectAsState()
    val playbackFailure by viewModel.playbackFailure.collectAsState()
    val subtitleDelayMs by viewModel.subtitleDelayMs.collectAsState()
    val player = viewModel.player
    val hostView = LocalView.current
    var isQuickMenuVisible by remember { mutableStateOf(false) }
    var videoResizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }
    var playbackState by remember(player) { mutableIntStateOf(player.playbackState) }
    val playerFocusRequester = remember { FocusRequester() }
    val controlsFocusRequester = remember { FocusRequester() }
    val quickMenuFocusRequester = remember { FocusRequester() }
    val noticeFocusRequester = remember { FocusRequester() }
    val noticeVisible = compatibilityWarning != null || playbackFailure != null

    LaunchedEffect(mediaId) {
        viewModel.loadMedia(mediaId, startFromBeginning)
    }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        // A TV can otherwise enter the system dream/screensaver while a film is
        // playing without remote input. View.keepScreenOn maps to Android's
        // FLAG_KEEP_SCREEN_ON and is cleared as soon as this player route leaves
        // composition, so normal idle behaviour resumes elsewhere in Aperture.
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
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED && !hasReturned) {
                    hasReturned = true
                    viewModel.saveProgressNow(markCompleted = true)
                    onFinished()
                }
            }
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
            }
            isOsdVisible -> viewModel.hideOsd()
            else -> {
                if (player.isPlaying) player.pause()
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
                if (noticeVisible) {
                    false
                } else if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (isOsdVisible && !isQuickMenuVisible &&
                        keyEvent.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK
                    ) {
                        viewModel.showOsdBriefly()
                    }
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (!isOsdVisible && !isQuickMenuVisible) {
                                viewModel.showOsdBriefly()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!isQuickMenuVisible && !isOsdVisible) {
                                viewModel.seekBackward()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!isQuickMenuVisible && !isOsdVisible) {
                                viewModel.seekForward()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!isQuickMenuVisible && !isOsdVisible) {
                                viewModel.showOsdBriefly()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            // BackHandler owns the layered close behaviour.
                            false
                        }
                        else -> false
                    }
                } else false
            }
            .focusRequester(playerFocusRequester)
            .focusable()
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    subtitleView?.visibility = View.GONE
                    this.player = player
                }
            },
            update = { view ->
                view.useController = false
                view.subtitleView?.visibility = View.GONE
                view.player = player
                view.resizeMode = videoResizeMode.media3Mode
            },
            modifier = Modifier.fillMaxSize()
        )

        SubtitleOverlay(player = player, style = subtitleStyle)

        AnimatedVisibility(
            visible = media != null && playbackState != androidx.media3.common.Player.STATE_READY &&
                playbackState != androidx.media3.common.Player.STATE_ENDED,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BufferingOverlay(media = media)
        }

        // OSD
        AnimatedVisibility(
            visible = isOsdVisible && !isQuickMenuVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerOsd(
                media = media,
                player = player,
                controlsFocusRequester = controlsFocusRequester,
                onInteraction = viewModel::showOsdBriefly,
                onRestart = {
                    player.seekTo(0)
                    viewModel.saveProgressNow()
                    player.play()
                    viewModel.showOsdBriefly()
                },
                onQuickMenu = {
                    isQuickMenuVisible = true
                    viewModel.toggleOsd()
                }
            )
        }

        // Quick Menu
        AnimatedVisibility(
            visible = isQuickMenuVisible,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            QuickMenu(
                player = player,
                focusRequester = quickMenuFocusRequester,
                onlineSubtitleState = onlineSubtitles,
                openSubtitlesSession = openSubtitlesSession,
                videoResizeMode = videoResizeMode,
                onVideoResizeModeSelected = { videoResizeMode = it },
                subtitleDelayMs = subtitleDelayMs,
                onSubtitleDelayDecrease = {
                    viewModel.adjustSubtitleDelay(-PlayerViewModel.SYNC_STEP_MS)
                },
                onSubtitleDelayIncrease = {
                    viewModel.adjustSubtitleDelay(PlayerViewModel.SYNC_STEP_MS)
                },
                onSubtitleDelayReset = viewModel::resetSubtitleDelay,
                onSearchOnline = viewModel::searchOpenSubtitles,
                onDownloadOnline = viewModel::downloadOpenSubtitle
            )
        }

        compatibilityWarning?.let { warning ->
            PlaybackNotice(
                title = warning.title,
                message = warning.message,
                safeLabel = "Go Back",
                proceedLabel = warning.proceedLabel,
                safeFocusRequester = noticeFocusRequester,
                onSafe = {
                    viewModel.dismissCompatibilityWarning()
                    onBack()
                },
                onProceed = viewModel::playDespiteWarning
            )
        }

        if (compatibilityWarning == null) {
            playbackFailure?.let { failure ->
                PlaybackNotice(
                    title = failure.title,
                    message = failure.message,
                    safeLabel = "Go Back",
                    proceedLabel = "Retry",
                    safeFocusRequester = noticeFocusRequester,
                    onSafe = {
                        viewModel.dismissPlaybackFailure()
                        onBack()
                    },
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
        ImageRequest.Builder(context)
            .data(
                TmdbApi.IMAGE_BASE_URL +
                    (if (media?.backdropPath != null) "w1280" else "w780") + path
            )
            .crossfade(true)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        artworkModel?.let { model ->
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp)
            )
            Text(
                text = media?.title ?: "Preparing playback…",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Text(
                text = "Buffering…",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun PlaybackNotice(
    title: String,
    message: String,
    safeLabel: String,
    proceedLabel: String,
    safeFocusRequester: FocusRequester,
    onSafe: () -> Unit,
    onProceed: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 680.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
            ),
            shape = RoundedCornerShape(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onSafe,
                        modifier = Modifier.focusRequester(safeFocusRequester)
                    ) {
                        Text(safeLabel)
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = onProceed) {
                        Text(proceedLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickMenu(
    player: androidx.media3.common.Player,
    focusRequester: FocusRequester,
    onlineSubtitleState: OnlineSubtitleState,
    openSubtitlesSession: OpenSubtitlesSessionState,
    videoResizeMode: VideoResizeMode,
    onVideoResizeModeSelected: (VideoResizeMode) -> Unit,
    subtitleDelayMs: Long,
    onSubtitleDelayDecrease: () -> Unit,
    onSubtitleDelayIncrease: () -> Unit,
    onSubtitleDelayReset: () -> Unit,
    onSearchOnline: () -> Unit,
    onDownloadOnline: (OnlineSubtitleOption) -> Unit
) {
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) { tracks = newTracks }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.54f)
            .padding(horizontal = 32.dp, vertical = 20.dp),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            QuickMenuColumn(
                title = "Audio",
                icon = Icons.Rounded.Audiotrack,
                items = getTrackItems(tracks, C.TRACK_TYPE_AUDIO)
                    .filter { it.isSupported },
                emptyLabel = "No compatible audio tracks",
                onItemSelected = { trackGroup, index ->
                    if (trackGroup.isTrackSupported(index)) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .setOverrideForType(
                                TrackSelectionOverride(
                                    trackGroup.mediaTrackGroup,
                                    index
                                )
                            )
                            .build()
                    }
                }
            )
            
            QuickMenuColumn(
                title = "Subtitles",
                icon = Icons.Rounded.Subtitles,
                items = getTrackItems(tracks, C.TRACK_TYPE_TEXT)
                    .filter { it.isSupported },
                emptyLabel = "No compatible subtitle tracks",
                headerContent = {
                    TimingAdjustmentControl(
                        label = "Subtitle sync",
                        valueMs = subtitleDelayMs,
                        supportingText = "Negative values show subs earlier",
                        focusRequester = focusRequester,
                        onDecrease = onSubtitleDelayDecrease,
                        onIncrease = onSubtitleDelayIncrease,
                        onReset = onSubtitleDelayReset
                    )
                },
                onItemSelected = { trackGroup, index ->
                    if (trackGroup.isTrackSupported(index)) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                TrackSelectionOverride(
                                    trackGroup.mediaTrackGroup,
                                    index
                                )
                            )
                            .build()
                    }
                },
                onDisable = {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                },
                disableLabel = "Off"
            )

            OnlineSubtitlesColumn(
                state = onlineSubtitleState,
                session = openSubtitlesSession,
                onSearch = onSearchOnline,
                onDownload = onDownloadOnline
            )

            PlaybackOptionsColumn(
                selectedResizeMode = videoResizeMode,
                onResizeModeSelected = onVideoResizeModeSelected
            )
        }
    }
}

private enum class VideoResizeMode(
    val label: String,
    val description: String,
    val media3Mode: Int
) {
    FIT("Fit", "Show the complete picture", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Stretch", "Fill the screen without preserving shape", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", "Fill the screen and crop the edges", AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
}

@Composable
private fun RowScope.PlaybackOptionsColumn(
    selectedResizeMode: VideoResizeMode,
    onResizeModeSelected: (VideoResizeMode) -> Unit
) {
    Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AspectRatio, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Picture", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VideoResizeMode.entries) { resizeMode ->
                Surface(
                    onClick = { onResizeModeSelected(resizeMode) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (resizeMode == selectedResizeMode) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(resizeMode.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            resizeMode.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

data class TrackItem(
    val name: String,
    val isSelected: Boolean,
    val isSupported: Boolean,
    val group: Tracks.Group,
    val index: Int
)

@Composable
private fun RowScope.QuickMenuColumn(
    title: String,
    icon: ImageVector,
    items: List<Any>,
    onItemSelected: (Tracks.Group, Int) -> Unit,
    emptyLabel: String = "No tracks found",
    headerContent: (@Composable () -> Unit)? = null,
    onDisable: (() -> Unit)? = null,
    disableLabel: String? = null
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        headerContent?.let {
            Spacer(Modifier.height(12.dp))
            it()
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (items.isEmpty()) {
                item {
                    Text(
                        emptyLabel,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (onDisable != null && disableLabel != null) {
                item {
                    Surface(
                        onClick = onDisable,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp))
                    ) { Text(disableLabel, modifier = Modifier.padding(8.dp)) }
                }
            }
            items(items) { item ->
                val label = if (item is TrackItem) item.name else item.toString()
                val isSelected = if (item is TrackItem) item.isSelected else false
                
                Surface(
                    onClick = { 
                        if (item is TrackItem) onItemSelected(item.group, item.index)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TimingAdjustmentControl(
    label: String,
    valueMs: Long,
    supportingText: String,
    focusRequester: FocusRequester? = null,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimingButton(
                label = "−",
                onClick = onDecrease,
                modifier = if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            TimingButton(
                label = formatDelay(valueMs),
                onClick = onReset,
                modifier = Modifier.weight(1f)
            )
            TimingButton(label = "+", onClick = onIncrease)
        }
        Text(
            supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun TimingButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(19.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
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
private fun RowScope.OnlineSubtitlesColumn(
    state: OnlineSubtitleState,
    session: OpenSubtitlesSessionState,
    onSearch: () -> Unit,
    onDownload: (OnlineSubtitleOption) -> Unit
) {
    Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("OpenSubtitles", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        if (session !is OpenSubtitlesSessionState.SignedIn) {
            Text(
                "Sign in from Settings",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Surface(
                onClick = onSearch,
                enabled = state !is OnlineSubtitleState.Loading &&
                    state !is OnlineSubtitleState.Downloading,
                modifier = Modifier.fillMaxWidth(),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp))
            ) {
                Text(
                    if (state is OnlineSubtitleState.Loading) "Searching…" else "Search online",
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is OnlineSubtitleState.Results -> {
                        if (state.options.isEmpty()) {
                            item { Text("No subtitles found", style = MaterialTheme.typography.bodySmall) }
                        }
                        items(state.options, key = { it.fileId }) { option ->
                            Surface(
                                onClick = { onDownload(option) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp))
                            ) {
                                Text(option.label, modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                    is OnlineSubtitleState.Downloading -> item {
                        Text("Downloading ${state.label}…", style = MaterialTheme.typography.bodySmall)
                    }
                    is OnlineSubtitleState.Attached -> item {
                        Text("Attached ${state.label}", style = MaterialTheme.typography.bodySmall)
                    }
                    is OnlineSubtitleState.Error -> item {
                        Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun SubtitleOverlay(
    player: androidx.media3.common.Player,
    style: PlayerSubtitleStyle
) {
    var cues by remember(player) { mutableStateOf(CueGroup.EMPTY_TIME_ZERO) }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onCues(cueGroup: CueGroup) { cues = cueGroup }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val textColour = when (style.colour) {
        "yellow" -> android.graphics.Color.YELLOW
        "cyan" -> android.graphics.Color.CYAN
        else -> android.graphics.Color.WHITE
    }
    val backgroundColour = android.graphics.Color.argb(
        (style.backgroundOpacity.coerceIn(0f, 0.9f) * 255).toInt(), 12, 12, 14
    )
    AndroidView(
        factory = { context ->
            SubtitleView(context).apply {
                // Use Aperture's subtitle appearance settings instead of
                // letting embedded cue styling replace the configured colors.
                setApplyEmbeddedStyles(false)
            }
        },
        update = { view ->
            view.setCues(cues.cues)
            view.setFractionalTextSize(0.0533f * style.textScale)
            view.setStyle(
                CaptionStyleCompat(
                    textColour,
                    android.graphics.Color.TRANSPARENT,
                    backgroundColour,
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                    android.graphics.Color.BLACK,
                    android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                )
            )
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun getTrackItems(tracks: Tracks, type: Int): List<TrackItem> {
    val items = mutableListOf<TrackItem>()
    tracks.groups.forEach { group ->
        if (group.type == type) {
            for (i in 0 until group.length) {
                items.add(
                    TrackItem(
                        name = group.getTrackFormat(i).let { format ->
                            format.label ?: format.language?.uppercase() ?: when (type) {
                                C.TRACK_TYPE_AUDIO -> "Audio ${i + 1}"
                                C.TRACK_TYPE_TEXT -> "Subtitle ${i + 1}"
                                else -> "Track ${i + 1}"
                            }
                        },
                        isSelected = group.isTrackSelected(i),
                        isSupported = group.isTrackSupported(i),
                        group = group,
                        index = i
                    )
                )
            }
        }
    }
    return items
}

@Composable
private fun PlayerOsd(
    media: MediaEntity?,
    player: androidx.media3.common.Player,
    controlsFocusRequester: FocusRequester,
    onInteraction: () -> Unit,
    onRestart: () -> Unit,
    onQuickMenu: () -> Unit
) {
    var currentPosition by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration) }
    var isPlaying by remember { mutableStateOf(player.playWhenReady) }

    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration
            isPlaying = player.playWhenReady
            kotlinx.coroutines.delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(48.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                text = media?.title ?: "",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

            val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(currentPosition), color = Color.White)
                Text(formatTime(duration), color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerControlIconButton(
                    icon = Icons.Rounded.Replay,
                    contentDescription = "Restart",
                    onClick = onRestart
                )
                Spacer(modifier = Modifier.width(24.dp))
                PlayerControlIconButton(
                    icon = Icons.Rounded.FastRewind,
                    contentDescription = "Rewind",
                    onClick = {
                        player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                        onInteraction()
                    }
                )
                Spacer(modifier = Modifier.width(32.dp))
                PlayerControlIconButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    iconSize = 64.dp,
                    onClick = {
                        if (isPlaying) player.pause() else player.play()
                        onInteraction()
                    },
                    modifier = Modifier.focusRequester(controlsFocusRequester)
                )
                Spacer(modifier = Modifier.width(32.dp))
                PlayerControlIconButton(
                    icon = Icons.Rounded.FastForward,
                    contentDescription = "Fast Forward",
                    onClick = {
                        val safeDuration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        player.seekTo((player.currentPosition + 10000).coerceAtMost(safeDuration))
                        onInteraction()
                    }
                )
                Spacer(modifier = Modifier.width(32.dp))
                PlayerControlIconButton(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = "Audio and subtitle options",
                    onClick = onQuickMenu
                )
            }
        }
    }
}

@Composable
private fun PlayerControlIconButton(
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

private fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
