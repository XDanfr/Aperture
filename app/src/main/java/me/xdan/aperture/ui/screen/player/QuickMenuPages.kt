package me.xdan.aperture.ui.screen.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import java.util.Locale

private enum class QuickMenuVideoResizeMode(
    val label: String,
    val description: String,
    val media3Mode: Int
) {
    FIT("Fit", "Show the complete picture", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Stretch", "Fill the screen without preserving shape", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", "Fill the screen and crop the edges", AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickMenuPages(
    player: androidx.media3.common.Player,
    focusRequester: FocusRequester,
    subtitleDelayMs: Long,
    onSubtitleDelayDecrease: () -> Unit,
    onSubtitleDelayIncrease: () -> Unit,
    onSubtitleDelayReset: () -> Unit,
    videoResizeMode: Int,
    onVideoResizeModeSelected: (Int) -> Unit
) {
    var page by remember { mutableStateOf<QuickMenuPage>(QuickMenuPage.Categories) }
    val pageFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = page != QuickMenuPage.Categories) {
        page = QuickMenuPage.Categories
    }

    LaunchedEffect(page) {
        if (page == QuickMenuPage.Categories) {
            focusRequester.requestFocus()
        } else {
            pageFocusRequester.requestFocus()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.62f)
            .padding(horizontal = 32.dp, vertical = 20.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
        )
    ) {
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 4 },
                    initialContentExit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                        slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 6 }
                )
            },
            label = "quickMenuPageTransition"
        ) { currentPage ->
            when (currentPage) {
                QuickMenuPage.Categories -> QuickMenuCategories(
                    focusRequester = focusRequester,
                    onSelected = { page = it }
                )
                QuickMenuPage.Audio -> QuickMenuTrackPage(
                    title = "Audio",
                    type = C.TRACK_TYPE_AUDIO,
                    player = player,
                    firstFocusRequester = pageFocusRequester,
                    emptyLabel = "No compatible audio tracks"
                )
                QuickMenuPage.Subtitles -> QuickMenuSubtitlesPage(
                    player = player,
                    firstFocusRequester = pageFocusRequester,
                    subtitleDelayMs = subtitleDelayMs,
                    onSubtitleDelayDecrease = onSubtitleDelayDecrease,
                    onSubtitleDelayIncrease = onSubtitleDelayIncrease,
                    onSubtitleDelayReset = onSubtitleDelayReset
                )
                QuickMenuPage.Playback -> QuickMenuPlaybackPage(
                    player = player,
                    firstFocusRequester = pageFocusRequester
                )
                QuickMenuPage.Video -> QuickMenuVideoPage(
                    selectedResizeMode = videoResizeMode,
                    firstFocusRequester = pageFocusRequester,
                    onSelected = onVideoResizeModeSelected
                )
                QuickMenuPage.Other -> QuickMenuOtherPage(firstFocusRequester = pageFocusRequester)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuCategories(
    focusRequester: FocusRequester,
    onSelected: (QuickMenuPage) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Quick Menu", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Choose a category",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            quickMenuCategories.chunked(2).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEachIndexed { columnIndex, category ->
                        Surface(
                            onClick = { onSelected(category.page) },
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (rowIndex == 0 && columnIndex == 0) {
                                        Modifier.focusRequester(focusRequester)
                                    } else Modifier
                                ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(category.icon, contentDescription = null)
                                Text(category.title, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuTrackPage(
    title: String,
    type: Int,
    player: androidx.media3.common.Player,
    firstFocusRequester: FocusRequester,
    emptyLabel: String
) {
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }

    androidx.compose.runtime.DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val items = getQuickMenuTrackItems(tracks, type).filter { it.isSupported }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (items.isEmpty()) {
                item {
                    Text(
                        emptyLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            items(items, key = { "${it.group.mediaTrackGroup.id}-${it.index}" }) { item ->
                Surface(
                    onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(type, false)
                            .setOverrideForType(
                                TrackSelectionOverride(item.group.mediaTrackGroup, item.index)
                            )
                            .build()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (item == items.firstOrNull()) Modifier.focusRequester(firstFocusRequester) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (item.isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(item.name, modifier = Modifier.padding(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuSubtitlesPage(
    player: androidx.media3.common.Player,
    firstFocusRequester: FocusRequester,
    subtitleDelayMs: Long,
    onSubtitleDelayDecrease: () -> Unit,
    onSubtitleDelayIncrease: () -> Unit,
    onSubtitleDelayReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Subtitles", style = MaterialTheme.typography.headlineMedium)

        TimingAdjustmentControl(
            label = "Subtitle sync",
            valueMs = subtitleDelayMs,
            supportingText = "Negative values show subtitles earlier",
            onDecrease = onSubtitleDelayDecrease,
            onIncrease = onSubtitleDelayIncrease,
            onReset = onSubtitleDelayReset,
            focusRequester = firstFocusRequester
        )

        QuickMenuTrackPage(
            title = "Subtitle tracks",
            type = C.TRACK_TYPE_TEXT,
            player = player,
            firstFocusRequester = remember { FocusRequester() },
            emptyLabel = "No compatible subtitle tracks"
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuPlaybackPage(
    player: androidx.media3.common.Player,
    firstFocusRequester: FocusRequester
) {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }

    androidx.compose.runtime.DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Playback", style = MaterialTheme.typography.headlineMedium)
        QuickMenuAction("Restart", Icons.Rounded.Replay, { player.seekTo(0) }, firstFocusRequester)
        QuickMenuAction("Rewind 10 seconds", Icons.Rounded.FastRewind, { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) })
        QuickMenuAction("${if (isPlaying) "Pause" else "Play"}", if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, {
            if (player.isPlaying) player.pause() else player.play()
        })
        QuickMenuAction("Forward 10 seconds", Icons.Rounded.FastForward, {
            val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
            player.seekTo((player.currentPosition + 10_000L).coerceAtMost(duration))
        })
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuVideoPage(
    selectedResizeMode: Int,
    firstFocusRequester: FocusRequester,
    onSelected: (Int) -> Unit
) {
    val options = QuickMenuVideoResizeMode.entries
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AspectRatio, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Video", style = MaterialTheme.typography.headlineMedium)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(options) { option ->
                Surface(
                    onClick = { onSelected(option.media3Mode) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (option.media3Mode == options.first().media3Mode) Modifier.focusRequester(firstFocusRequester) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (option.media3Mode == selectedResizeMode) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(option.label, style = MaterialTheme.typography.titleMedium)
                        Text(option.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuOtherPage(firstFocusRequester: FocusRequester) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Other", style = MaterialTheme.typography.headlineMedium)
        Surface(
            onClick = {},
            modifier = Modifier.fillMaxWidth().focusRequester(firstFocusRequester),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text("More player options can live here", modifier = Modifier.padding(18.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private data class QuickMenuTrackItem(
    val name: String,
    val isSelected: Boolean,
    val isSupported: Boolean,
    val group: Tracks.Group,
    val index: Int
)

private fun getQuickMenuTrackItems(tracks: Tracks, type: Int): List<QuickMenuTrackItem> {
    val result = mutableListOf<QuickMenuTrackItem>()
    tracks.groups.forEach { group ->
        if (group.type != type) return@forEach
        for (index in 0 until group.length) {
            val format = group.getTrackFormat(index)
            result += QuickMenuTrackItem(
                name = format.label ?: format.language?.uppercase() ?: when (type) {
                    C.TRACK_TYPE_AUDIO -> "Audio ${index + 1}"
                    C.TRACK_TYPE_TEXT -> "Subtitle ${index + 1}"
                    else -> "Track ${index + 1}"
                },
                isSelected = group.isTrackSelected(index),
                isSupported = group.isTrackSupported(index),
                group = group,
                index = index
            )
        }
    }
    return result
}
