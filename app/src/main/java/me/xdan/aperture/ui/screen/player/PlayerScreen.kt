package me.xdan.aperture.ui.screen.player

import android.net.Uri
import android.view.KeyEvent
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import me.xdan.aperture.data.local.entity.MediaEntity
import java.io.File

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    mediaId: Long,
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val media by viewModel.media.collectAsState()
    val isOsdVisible by viewModel.isOsdVisible.collectAsState()
    var isQuickMenuVisible by remember { mutableStateOf(false) }
    val player = viewModel.player

    LaunchedEffect(mediaId) {
        viewModel.loadMedia(mediaId)
    }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (!isQuickMenuVisible && keyEvent.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK) {
                        viewModel.showOsdBriefly()
                    }
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (!isQuickMenuVisible) {
                                viewModel.toggleOsd()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!isQuickMenuVisible) {
                                viewModel.seekBackward()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!isQuickMenuVisible) {
                                viewModel.seekForward()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!isQuickMenuVisible) {
                                isQuickMenuVisible = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (isQuickMenuVisible) {
                                isQuickMenuVisible = false
                                true
                            } else {
                                if (player.isPlaying) {
                                    player.pause()
                                }
                                onBack()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = false
                    // Set surface type to ensure compatibility
                    // (Default is SurfaceView, which is usually best, but TextureView can sometimes fix 'green screen' issues if there are z-order problems)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // OSD
        AnimatedVisibility(
            visible = isOsdVisible && !isQuickMenuVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerOsd(
                media = media,
                player = player
            )
        }

        // Quick Menu
        AnimatedVisibility(
            visible = isQuickMenuVisible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            QuickMenu(
                player = player
            )
        }
    }
}

@Composable
private fun QuickMenu(
    player: androidx.media3.common.Player
) {
    val tracks = player.currentTracks
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp),
        colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
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
                title = "OpenSubtitles",
                icon = Icons.Rounded.Download,
                items = listOf("Search Online"),
                onItemSelected = { _, _ -> /* TODO */ }
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
    onItemSelected: (Tracks.Group, Int) -> Unit
) {
    Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                val label = if (item is TrackItem) item.name else item.toString()
                val isSelected = if (item is TrackItem) item.isSelected else false
                
                Surface(
                    onClick = { 
                        if (item is TrackItem) onItemSelected(item.group, item.index)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
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

private fun getTrackItems(tracks: Tracks, type: Int): List<TrackItem> {
    val items = mutableListOf<TrackItem>()
    tracks.groups.forEach { group ->
        if (group.type == type) {
            for (i in 0 until group.length) {
                items.add(
                    TrackItem(
                        name = group.getTrackFormat(i).language ?: "Unknown (${i+1})",
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
    player: androidx.media3.common.Player
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
                IconButton(onClick = { player.seekTo(player.currentPosition - 10000) }) {
                    Icon(Icons.Rounded.FastRewind, contentDescription = "Rewind", tint = Color.White, modifier = Modifier.size(48.dp))
                }
                Spacer(modifier = Modifier.width(32.dp))
                IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Spacer(modifier = Modifier.width(32.dp))
                IconButton(onClick = { player.seekTo(player.currentPosition + 10000) }) {
                    Icon(Icons.Rounded.FastForward, contentDescription = "Fast Forward", tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
