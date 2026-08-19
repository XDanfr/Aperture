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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
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
    onVideoResizeModeSelected: (Int) -> Unit,
    onClose: () -> Unit
) {
    var page by remember { mutableStateOf<QuickMenuPage>(QuickMenuPage.Categories) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val pageFocusRequester = remember { FocusRequester() }
    val closeFocusRequester = remember { FocusRequester() }
    val categoryFocusRequesters = remember {
        List(quickMenuCategories.size) { FocusRequester() }
    }
    LaunchedEffect(Unit) {
        categoryFocusRequesters.first().requestFocus()
    }

    BackHandler(enabled = page != QuickMenuPage.Categories) {
        page = QuickMenuPage.Categories
    }

    LaunchedEffect(page, selectedCategoryIndex) {
        withFrameNanos { }
        withFrameNanos { }
        if (page == QuickMenuPage.Categories) {
            if (selectedCategoryIndex == 0) {
                focusRequester.requestFocus()
            } else {
                categoryFocusRequesters[selectedCategoryIndex].requestFocus()
            }
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
        colors = androidx.tv.material3.SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .focusRequester(closeFocusRequester)
                    .focusable()
                    .onFocusChanged { if (it.isFocused) onClose() }
            )

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
                        closeFocusRequester = closeFocusRequester,
                        categoryFocusRequesters = categoryFocusRequesters,
                        selectedCategoryIndex = selectedCategoryIndex,
                        onSelected = { index, selectedPage ->
                            selectedCategoryIndex = index
                            page = selectedPage
                        }
                    )
                    QuickMenuPage.Audio -> QuickMenuTrackPage(
                        title = "Audio",
                        type = C.TRACK_TYPE_AUDIO,
                        player = player,
                        firstFocusRequester = pageFocusRequester,
                        closeFocusRequester = closeFocusRequester,
                        emptyLabel = "No audio tracks available."
                    )
                    QuickMenuPage.Subtitles -> QuickMenuSubtitlesPage(
                        player = player,
                        firstFocusRequester = pageFocusRequester,
                        closeFocusRequester = closeFocusRequester,
                        subtitleDelayMs = subtitleDelayMs,
                        onSubtitleDelayDecrease = onSubtitleDelayDecrease,
                        onSubtitleDelayIncrease = onSubtitleDelayIncrease,
                        onSubtitleDelayReset = onSubtitleDelayReset
                    )
                    QuickMenuPage.Playback -> QuickMenuPlaybackPage(
                        player = player,
                        firstFocusRequester = pageFocusRequester,
                        closeFocusRequester = closeFocusRequester
                    )
                    QuickMenuPage.Video -> QuickMenuVideoPage(
                        selectedResizeMode = videoResizeMode,
                        firstFocusRequester = pageFocusRequester,
                        closeFocusRequester = closeFocusRequester,
                        onSelected = onVideoResizeModeSelected
                    )
                    QuickMenuPage.Other -> QuickMenuOtherPage(
                        firstFocusRequester = pageFocusRequester,
                        closeFocusRequester = closeFocusRequester
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuCategories(
    focusRequester: FocusRequester,
    closeFocusRequester: FocusRequester,
    categoryFocusRequesters: List<FocusRequester>,
    selectedCategoryIndex: Int,
    onSelected: (Int, QuickMenuPage) -> Unit
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            quickMenuCategories.forEachIndexed { index, category ->
                Surface(
                    onClick = { onSelected(index, category.page) },
                    modifier = Modifier
                        .weight(1f)
                        .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier.focusRequester(categoryFocusRequesters[index]))
                        .focusProperties {
                            up = closeFocusRequester
                            left = if (index == 0) categoryFocusRequesters[index] else categoryFocusRequesters[index - 1]
                            right = if (index == quickMenuCategories.lastIndex) categoryFocusRequesters[index] else categoryFocusRequesters[index + 1]
                        },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                        pressedContainerColor = MaterialTheme.colorScheme.primary,
                        pressedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(category.icon, contentDescription = null)
                        Text(category.title, style = MaterialTheme.typography.titleMedium)
                    }
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
    closeFocusRequester: FocusRequester? = null,
    emptyLabel: String,
    headerContent: (@Composable () -> Unit)? = null
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
    val selectedItem = items.firstOrNull { it.isSelected }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    emptyLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return
        }

        Text(
            "Selected: ${selectedItem?.name ?: "None"}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        headerContent?.invoke()

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        .then(if (item == items.firstOrNull()) Modifier.focusRequester(firstFocusRequester) else Modifier)
                        .focusProperties {
                            if (items.indexOf(item) < 3 && closeFocusRequester != null) {
                                up = closeFocusRequester
                            }
                        },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (item.isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                        pressedContainerColor = MaterialTheme.colorScheme.primary,
                        pressedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        item.name,
                        modifier = Modifier.padding(18.dp),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuTimingAdjustmentControl(
    label: String,
    valueMs: Long,
    supportingText: String,
    focusRequester: FocusRequester,
    closeFocusRequester: FocusRequester? = null,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onDecrease,
                modifier = Modifier
                    .height(40.dp)
                    .focusRequester(focusRequester)
                    .focusProperties { if (closeFocusRequester != null) up = closeFocusRequester },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                    pressedContainerColor = MaterialTheme.colorScheme.primary,
                    pressedContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("−", modifier = Modifier.padding(horizontal = 16.dp))
            }

            Surface(
                onClick = onReset,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                    pressedContainerColor = MaterialTheme.colorScheme.primary,
                    pressedContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(formatQuickMenuDelay(valueMs))
                }
            }

            Surface(
                onClick = onIncrease,
                modifier = Modifier
                    .height(40.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                    pressedContainerColor = MaterialTheme.colorScheme.primary,
                    pressedContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("+", modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        Text(
            supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatQuickMenuDelay(delayMs: Long): String = when {
    delayMs == 0L -> "0 ms"
    kotlin.math.abs(delayMs) < 1_000L ->
        String.format(Locale.getDefault(), "%+d ms", delayMs)
    else ->
        String.format(Locale.getDefault(), "%+.1f s", delayMs / 1_000f)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuSubtitlesPage(
    player: androidx.media3.common.Player,
    firstFocusRequester: FocusRequester,
    closeFocusRequester: FocusRequester,
    subtitleDelayMs: Long,
    onSubtitleDelayDecrease: () -> Unit,
    onSubtitleDelayIncrease: () -> Unit,
    onSubtitleDelayReset: () -> Unit
) {
    val trackFocusRequester = remember { FocusRequester() }

    QuickMenuTrackPage(
        title = "Subtitles",
        type = C.TRACK_TYPE_TEXT,
        player = player,
        firstFocusRequester = trackFocusRequester,
        closeFocusRequester = closeFocusRequester,
        emptyLabel = "No subtitle tracks available.",
        headerContent = {
            QuickMenuTimingAdjustmentControl(
                label = "Subtitle sync",
                valueMs = subtitleDelayMs,
                supportingText = "Negative values show subtitles earlier",
                onDecrease = onSubtitleDelayDecrease,
                onIncrease = onSubtitleDelayIncrease,
                onReset = onSubtitleDelayReset,
                focusRequester = firstFocusRequester,
                closeFocusRequester = closeFocusRequester
            )
        }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuPlaybackPage(
    player: androidx.media3.common.Player,
    firstFocusRequester: FocusRequester,
    closeFocusRequester: FocusRequester
) {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }

    androidx.compose.runtime.DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Text("Playback", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            QuickMenuAction(
                "Restart",
                Icons.Rounded.Replay,
                { player.seekTo(0) },
                firstFocusRequester,
                true,
                closeFocusRequester
            )
        }
        item {
            QuickMenuAction(
                "Rewind 10 seconds",
                Icons.Rounded.FastRewind,
                { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) },
                null,
                true,
                closeFocusRequester
            )
        }
        item {
            QuickMenuAction(
                "${if (isPlaying) "Pause" else "Play"}",
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                { if (player.isPlaying) player.pause() else player.play() },
                null,
                false,
                closeFocusRequester
            )
        }
        item {
            QuickMenuAction(
                "Forward 10 seconds",
                Icons.Rounded.FastForward,
                {
                    val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                    player.seekTo((player.currentPosition + 10_000L).coerceAtMost(duration))
                },
                null,
                false,
                closeFocusRequester
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuVideoPage(
    selectedResizeMode: Int,
    firstFocusRequester: FocusRequester,
    closeFocusRequester: FocusRequester,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.forEachIndexed { index, option ->
                Surface(
                    onClick = { onSelected(option.media3Mode) },
                    modifier = Modifier
                        .weight(1f)
                        .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier)
                        .focusProperties { up = closeFocusRequester },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (option.media3Mode == selectedResizeMode) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                        pressedContainerColor = MaterialTheme.colorScheme.primary,
                        pressedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(option.label, style = MaterialTheme.typography.titleMedium)
                        Text(option.description)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuOtherPage(
    firstFocusRequester: FocusRequester,
    closeFocusRequester: FocusRequester
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Other", style = MaterialTheme.typography.headlineMedium)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(firstFocusRequester)
                .focusable()
                .focusProperties { up = closeFocusRequester },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Coming soon!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    closeOnUp: Boolean = false,
    closeFocusRequester: FocusRequester? = null
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (closeOnUp && closeFocusRequester != null) up = closeFocusRequester
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            pressedContainerColor = MaterialTheme.colorScheme.primary,
            pressedContentColor = MaterialTheme.colorScheme.onPrimary
        )
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
