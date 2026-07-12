@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.view.KeyEvent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.text.CueGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.*
import me.xdan.aperture.data.local.entity.MediaEntity

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
    var isQuickMenuVisible by remember { mutableStateOf(false) }
    val player = viewModel.player
    val playerFocusRequester = remember { FocusRequester() }
    val controlsFocusRequester = remember { FocusRequester() }
    val quickMenuFocusRequester = remember { FocusRequester() }

    LaunchedEffect(mediaId) {
        viewModel.loadMedia(mediaId, startFromBeginning)
    }

    DisposableEffect(Unit) {
        onDispose {
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

    LaunchedEffect(isOsdVisible, isQuickMenuVisible) {
        if (isOsdVisible && !isQuickMenuVisible) {
            controlsFocusRequester.requestFocus()
        } else if (isQuickMenuVisible) {
            quickMenuFocusRequester.requestFocus()
        } else {
            playerFocusRequester.requestFocus()
        }
    }

    BackHandler {
        when {
            isQuickMenuVisible -> isQuickMenuVisible = false
            isOsdVisible -> viewModel.toggleOsd()
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
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
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
        PlayerSurface(
            player = player,
            modifier = Modifier.fillMaxSize()
        )

        SubtitleOverlay(player = player, style = subtitleStyle)

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
                onSearchOnline = viewModel::searchOpenSubtitles,
                onDownloadOnline = viewModel::downloadOpenSubtitle
            )
        }
    }
}

@Composable
private fun QuickMenu(
    player: androidx.media3.common.Player,
    focusRequester: FocusRequester,
    onlineSubtitleState: OnlineSubtitleState,
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
            .fillMaxHeight(0.46f)
            .padding(horizontal = 32.dp, vertical = 20.dp)
            .focusRequester(focusRequester)
            .focusable(),
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
                items = getTrackItems(tracks, C.TRACK_TYPE_AUDIO),
                onItemSelected = { trackGroup, index ->
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
            )
            
            QuickMenuColumn(
                title = "Subtitles",
                icon = Icons.Rounded.Subtitles,
                items = getTrackItems(tracks, C.TRACK_TYPE_TEXT),
                onItemSelected = { trackGroup, index ->
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
                onSearch = onSearchOnline,
                onDownload = onDownloadOnline
            )
        }
    }
}

data class TrackItem(
    val name: String,
    val isSelected: Boolean,
    val group: Tracks.Group,
    val index: Int
)

@Composable
private fun RowScope.QuickMenuColumn(
    title: String,
    icon: ImageVector,
    items: List<Any>,
    onItemSelected: (Tracks.Group, Int) -> Unit,
    onDisable: (() -> Unit)? = null,
    disableLabel: String? = null
) {
    Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun RowScope.OnlineSubtitlesColumn(
    state: OnlineSubtitleState,
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
        when (state) {
            OnlineSubtitleState.Idle -> Surface(
                onClick = onSearch,
                modifier = Modifier.fillMaxWidth(),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp))
            ) { Text("Search online", modifier = Modifier.padding(8.dp)) }
            OnlineSubtitleState.Loading -> Text("Searching…")
            is OnlineSubtitleState.Downloading -> Text("Downloading ${state.label}…")
            is OnlineSubtitleState.Attached -> {
                Text("Attached ${state.label}")
                Spacer(Modifier.height(8.dp))
                Surface(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                    Text("Search again", modifier = Modifier.padding(8.dp))
                }
            }
            is OnlineSubtitleState.Error -> {
                Text(state.message, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                Spacer(Modifier.height(8.dp))
                Surface(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again", modifier = Modifier.padding(8.dp))
                }
            }
            is OnlineSubtitleState.Results -> LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (state.options.isEmpty()) item { Text("No subtitles found.") }
                items(state.options, key = { it.fileId }) { option ->
                    Surface(
                        onClick = { onDownload(option) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp))
                    ) {
                        Text(option.label, modifier = Modifier.padding(8.dp), maxLines = 2)
                    }
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
            SubtitleView(context).apply { setApplyEmbeddedStyles(true) }
        },
        update = { view ->
            view.setCues(cues.cues)
            view.setFractionalTextSize(0.0533f * style.textScale)
            view.setStyle(
                CaptionStyleCompat(
                    textColour,
                    backgroundColour,
                    android.graphics.Color.TRANSPARENT,
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
