package me.xdan.aperture.ui.screen.player

import android.view.KeyEvent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import java.util.Locale
import me.xdan.aperture.data.subtitles.OpenSubtitlesSessionState
import me.xdan.aperture.ui.screen.settings.QuickMenuSubtitleAppearanceDialog
import me.xdan.aperture.ui.screen.settings.SettingsViewModel
import me.xdan.aperture.ui.screen.settings.SubtitleAppearanceSettings
import me.xdan.aperture.ui.theme.ApertureTheme

private enum class QuickMenuVideoResizeMode(
    val label: String,
    val description: String,
    val media3Mode: Int
) {
    FIT("Fit", "Show the complete picture", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Stretch", "Fill the screen without preserving shape", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", "Fill the screen and crop the edges", AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
}

private enum class QuickMenuSubtitleSubPage {
    MAIN,
    SYNC,
    OPENSUBTITLES
}

private enum class QuickMenuSubtitleFocusTarget {
    SYNC,
    CUSTOMISE,
    OPENSUBTITLES
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickMenuPages(
    player: PlayerEngine,
    settingsViewModel: SettingsViewModel,
    focusRequester: FocusRequester,
    subtitleDelayMs: Long,
    onSubtitleDelayDecrease: () -> Unit,
    onSubtitleDelayIncrease: () -> Unit,
    onSubtitleDelayReset: () -> Unit,
    videoResizeMode: Int,
    onVideoResizeModeSelected: (Int) -> Unit,
    onClose: () -> Unit,
    onLeavePlayerToOpenSubtitles: () -> Unit = {}
) {
    var page by remember { mutableStateOf<QuickMenuPage>(QuickMenuPage.Categories) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val pageFocusRequester = remember { FocusRequester() }
    val categoryFocusRequesters = remember {
        listOf(focusRequester) + List(quickMenuCategories.size - 1) { FocusRequester() }
    }
    val playerViewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val onlineSubtitleState by playerViewModel.onlineSubtitles.collectAsState()
    val openSubtitlesSession by playerViewModel.openSubtitlesSession.collectAsState()
    val subtitleAppearance by settingsViewModel.subtitleAppearance.collectAsState()

    BackHandler(enabled = page != QuickMenuPage.Categories) {
        page = QuickMenuPage.Categories
    }

    LaunchedEffect(page, selectedCategoryIndex) {
        withFrameNanos { }
        withFrameNanos { }
        if (page == QuickMenuPage.Categories) {
            categoryFocusRequesters[selectedCategoryIndex].requestFocus()
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
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
        )
    ) {
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 4 },
                    initialContentExit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) + slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 6 }
                )
            },
            label = "quickMenuPageTransition"
        ) { currentPage ->
            when (currentPage) {
                QuickMenuPage.Categories -> QuickMenuCategories(
                    focusRequesters = categoryFocusRequesters,
                    onSelected = { index, selectedPage ->
                        selectedCategoryIndex = index
                        page = selectedPage
                    },
                    onClose = onClose
                )
                QuickMenuPage.Audio -> QuickMenuTrackPage("Audio", C.TRACK_TYPE_AUDIO, player, pageFocusRequester, "No audio tracks available.", onClose)
                QuickMenuPage.Subtitles -> QuickMenuSubtitlesPage(
                    player = player,
                    firstFocusRequester = pageFocusRequester,
                    subtitleDelayMs = subtitleDelayMs,
                    onSubtitleDelayDecrease = onSubtitleDelayDecrease,
                    onSubtitleDelayIncrease = onSubtitleDelayIncrease,
                    onSubtitleDelayReset = onSubtitleDelayReset,
                    onClose = onClose,
                    onlineSubtitleState = onlineSubtitleState,
                    openSubtitlesSession = openSubtitlesSession,
                    subtitleAppearance = subtitleAppearance,
                    onSaveSubtitleAppearance = settingsViewModel::setSubtitleAppearance,
                    onSearchOnline = playerViewModel::searchOpenSubtitles,
                    onDownloadOnline = playerViewModel::downloadOpenSubtitle,
                    onLeavePlayerToOpenSubtitles = onLeavePlayerToOpenSubtitles
                )
                QuickMenuPage.Playback -> QuickMenuPlaybackPage(player, pageFocusRequester, onClose)
                QuickMenuPage.Video -> QuickMenuVideoPage(videoResizeMode, pageFocusRequester, onVideoResizeModeSelected, onClose)
                QuickMenuPage.Other -> QuickMenuOtherPage(pageFocusRequester, onClose)
            }
        }
    }
}

private fun Modifier.closeQuickMenuOnUp(onClose: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            onClose()
            true
        } else false
    }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuCategories(
    focusRequesters: List<FocusRequester>,
    onSelected: (Int, QuickMenuPage) -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Quick Menu", style = MaterialTheme.typography.headlineMedium)
        Text("Choose a category", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            quickMenuCategories.forEachIndexed { index, category ->
                Surface(
                    onClick = { onSelected(index, category.page) },
                    modifier = Modifier.weight(1f).focusRequester(focusRequesters[index]).closeQuickMenuOnUp(onClose).focusProperties {
                        left = if (index == 0) focusRequesters[index] else focusRequesters[index - 1]
                        right = if (index == quickMenuCategories.lastIndex) focusRequesters[index] else focusRequesters[index + 1]
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
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
private fun QuickMenuTrackPage(title: String, type: Int, player: PlayerEngine, firstFocusRequester: FocusRequester, emptyLabel: String, onClose: () -> Unit) {
    val tracks by player.tracks.collectAsState()
    val items = getQuickMenuTrackItems(tracks, type).filter { it.isSupported }
    val selectedItem = items.firstOrNull { it.isSelected }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (items.isEmpty()) { QuickMenuEmptyMessage(emptyLabel); return }
        Text("Selected: ${selectedItem?.name ?: "None"}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), contentPadding = PaddingValues(vertical = 14.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { "${it.group.mediaTrackGroup.id}-${it.index}" }) { item ->
                val isFirstRow = items.indexOf(item) < 3
                Surface(
                    onClick = {
                        player.setTrackTypeDisabled(type, false)
                        player.setTrackSelectionOverride(item.group.mediaTrackGroup, item.index)
                    },
                    modifier = Modifier.then(if (item == items.firstOrNull()) Modifier.focusRequester(firstFocusRequester) else Modifier).then(if (isFirstRow) Modifier.closeQuickMenuOnUp(onClose) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (item.isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                        pressedContainerColor = MaterialTheme.colorScheme.primary,
                        pressedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text(item.name, modifier = Modifier.padding(18.dp), maxLines = 2) }
            }
        }
    }
}

private fun formatQuickMenuDelay(delayMs: Long): String = when {
    delayMs == 0L -> "0 ms"
    kotlin.math.abs(delayMs) < 1_000L -> String.format(Locale.getDefault(), "%+d ms", delayMs)
    else -> String.format(Locale.getDefault(), "%+.1f s", delayMs / 1_000f)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuSubtitlesPage(
    player: PlayerEngine,
    firstFocusRequester: FocusRequester,
    subtitleDelayMs: Long,
    onSubtitleDelayDecrease: () -> Unit,
    onSubtitleDelayIncrease: () -> Unit,
    onSubtitleDelayReset: () -> Unit,
    onClose: () -> Unit,
    onlineSubtitleState: OnlineSubtitleState,
    openSubtitlesSession: OpenSubtitlesSessionState,
    subtitleAppearance: SubtitleAppearanceSettings,
    onSaveSubtitleAppearance: (SubtitleAppearanceSettings) -> Unit,
    onSearchOnline: () -> Unit,
    onDownloadOnline: (OnlineSubtitleOption) -> Unit,
    onLeavePlayerToOpenSubtitles: () -> Unit
) {
    val syncFocusRequester = remember { FocusRequester() }
    val customiseFocusRequester = remember { FocusRequester() }
    val openSubtitlesFocusRequester = remember { FocusRequester() }
    val trackFocusRequester = remember { FocusRequester() }
    val emptyFocusRequester = remember { FocusRequester() }
    val syncEarlierFocusRequester = remember { FocusRequester() }
    val syncResetFocusRequester = remember { FocusRequester() }
    val syncLaterFocusRequester = remember { FocusRequester() }
    val openSearchFocusRequester = remember { FocusRequester() }
    val openFirstResultFocusRequester = remember { FocusRequester() }
    var subPage by remember { mutableStateOf(QuickMenuSubtitleSubPage.MAIN) }
    var mainFocusTarget by remember { mutableStateOf(QuickMenuSubtitleFocusTarget.SYNC) }
    var showCustomise by remember { mutableStateOf(false) }

    BackHandler(enabled = subPage != QuickMenuSubtitleSubPage.MAIN) {
        mainFocusTarget = when (subPage) {
            QuickMenuSubtitleSubPage.SYNC -> QuickMenuSubtitleFocusTarget.SYNC
            QuickMenuSubtitleSubPage.OPENSUBTITLES -> QuickMenuSubtitleFocusTarget.OPENSUBTITLES
            QuickMenuSubtitleSubPage.MAIN -> QuickMenuSubtitleFocusTarget.SYNC
        }
        subPage = QuickMenuSubtitleSubPage.MAIN
    }

    LaunchedEffect(subPage, mainFocusTarget, showCustomise) {
        if (showCustomise) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        when (subPage) {
            QuickMenuSubtitleSubPage.MAIN -> when (mainFocusTarget) {
                QuickMenuSubtitleFocusTarget.SYNC -> syncFocusRequester.requestFocus()
                QuickMenuSubtitleFocusTarget.CUSTOMISE -> customiseFocusRequester.requestFocus()
                QuickMenuSubtitleFocusTarget.OPENSUBTITLES -> openSubtitlesFocusRequester.requestFocus()
            }
            QuickMenuSubtitleSubPage.SYNC -> syncEarlierFocusRequester.requestFocus()
            QuickMenuSubtitleSubPage.OPENSUBTITLES -> openSearchFocusRequester.requestFocus()
        }
    }

    AnimatedContent(targetState = subPage, modifier = Modifier.fillMaxSize(), transitionSpec = {
        ContentTransform(
            targetContentEnter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 5 },
            initialContentExit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) + slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 7 }
        )
    }, label = "quickMenuSubtitlePageTransition") { currentPage ->
        when (currentPage) {
            QuickMenuSubtitleSubPage.MAIN -> QuickMenuSubtitlesMainPage(
                player = player,
                syncFocusRequester = syncFocusRequester,
                customiseFocusRequester = customiseFocusRequester,
                openSubtitlesFocusRequester = openSubtitlesFocusRequester,
                trackFocusRequester = trackFocusRequester,
                emptyFocusRequester = emptyFocusRequester,
                onSync = { mainFocusTarget = QuickMenuSubtitleFocusTarget.SYNC; subPage = QuickMenuSubtitleSubPage.SYNC },
                onCustomise = { mainFocusTarget = QuickMenuSubtitleFocusTarget.CUSTOMISE; showCustomise = true },
                onOpenSubtitles = { mainFocusTarget = QuickMenuSubtitleFocusTarget.OPENSUBTITLES; subPage = QuickMenuSubtitleSubPage.OPENSUBTITLES },
                onClose = onClose
            )
            QuickMenuSubtitleSubPage.SYNC -> QuickMenuSubtitleSyncPage(
                earlierFocusRequester = syncEarlierFocusRequester,
                resetFocusRequester = syncResetFocusRequester,
                laterFocusRequester = syncLaterFocusRequester,
                subtitleDelayMs = subtitleDelayMs,
                onDecrease = onSubtitleDelayDecrease,
                onIncrease = onSubtitleDelayIncrease,
                onReset = onSubtitleDelayReset,
                onClose = onClose
            )
            QuickMenuSubtitleSubPage.OPENSUBTITLES -> QuickMenuOpenSubtitlesPage(
                searchFocusRequester = openSearchFocusRequester,
                firstResultFocusRequester = openFirstResultFocusRequester,
                state = onlineSubtitleState,
                session = openSubtitlesSession,
                onSearch = onSearchOnline,
                onDownload = onDownloadOnline,
                onLeavePlayerToOpenSubtitles = onLeavePlayerToOpenSubtitles,
                onClose = onClose
            )
        }
    }

    if (showCustomise) {
        QuickMenuSubtitleAppearanceDialog(
            initial = subtitleAppearance,
            onSave = { onSaveSubtitleAppearance(it); showCustomise = false },
            onDismiss = { showCustomise = false }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuSubtitlesMainPage(
    player: PlayerEngine,
    syncFocusRequester: FocusRequester,
    customiseFocusRequester: FocusRequester,
    openSubtitlesFocusRequester: FocusRequester,
    trackFocusRequester: FocusRequester,
    emptyFocusRequester: FocusRequester,
    onSync: () -> Unit,
    onCustomise: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onClose: () -> Unit
) {
    val tracks by player.tracks.collectAsState()
    val items = getQuickMenuTrackItems(tracks, C.TRACK_TYPE_TEXT).filter { it.isSupported }
    val selectedItem = items.firstOrNull { it.isSelected }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Subtitles", style = MaterialTheme.typography.headlineMedium)
        Text("Selected: ${selectedItem?.name ?: "None"}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickMenuAction("Sync", Icons.Rounded.Sync, onSync, focusRequester = syncFocusRequester, onClose = onClose, closeOnUp = true, modifier = Modifier.weight(1f).focusProperties { left = syncFocusRequester; right = customiseFocusRequester; down = if (items.isEmpty()) emptyFocusRequester else trackFocusRequester })
            QuickMenuAction("Customise", Icons.Rounded.FormatColorText, onCustomise, focusRequester = customiseFocusRequester, onClose = onClose, closeOnUp = true, modifier = Modifier.weight(1f).focusProperties { left = syncFocusRequester; right = openSubtitlesFocusRequester; down = if (items.isEmpty()) emptyFocusRequester else trackFocusRequester })
            QuickMenuAction("OpenSubtitles", Icons.Rounded.CloudDownload, onOpenSubtitles, focusRequester = openSubtitlesFocusRequester, onClose = onClose, closeOnUp = true, modifier = Modifier.weight(1f).focusProperties { left = customiseFocusRequester; right = openSubtitlesFocusRequester; down = if (items.isEmpty()) emptyFocusRequester else trackFocusRequester })
        }
        if (items.isEmpty()) {
            Surface(
                onClick = {},
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).focusRequester(emptyFocusRequester).focusable().focusProperties { up = syncFocusRequester; left = syncFocusRequester; right = openSubtitlesFocusRequester },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.primary, focusedContentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("No local subtitles available", style = MaterialTheme.typography.bodyLarge) }
            }
            return
        }
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp), contentPadding = PaddingValues(vertical = 14.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { "${it.group.mediaTrackGroup.id}-${it.index}" }) { item ->
                val itemIndex = items.indexOf(item)
                val isFirstRow = itemIndex < 3
                Surface(
                    onClick = {
                        player.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        player.setTrackSelectionOverride(item.group.mediaTrackGroup, item.index)
                    },
                    modifier = Modifier.then(if (item == items.firstOrNull()) Modifier.focusRequester(trackFocusRequester) else Modifier).focusProperties { if (isFirstRow) up = when (itemIndex) { 0 -> syncFocusRequester; 1 -> customiseFocusRequester; else -> openSubtitlesFocusRequester } },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = if (item.isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.primary, focusedContentColor = MaterialTheme.colorScheme.onPrimary, pressedContainerColor = MaterialTheme.colorScheme.primary, pressedContentColor = MaterialTheme.colorScheme.onPrimary)
                ) { Text(item.name, modifier = Modifier.padding(18.dp), maxLines = 2) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuSubtitleSyncPage(
    earlierFocusRequester: FocusRequester,
    resetFocusRequester: FocusRequester,
    laterFocusRequester: FocusRequester,
    subtitleDelayMs: Long,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Subtitle sync", style = MaterialTheme.typography.headlineMedium)
        Text("Adjust the subtitle timing for this video.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            QuickMenuAction("Earlier", Icons.Rounded.FastRewind, onDecrease, focusRequester = earlierFocusRequester, onClose = onClose, closeOnUp = true, modifier = Modifier.weight(1f).focusProperties { left = earlierFocusRequester; right = resetFocusRequester })
            Surface(onClick = onReset, modifier = Modifier.weight(1f).height(72.dp).focusRequester(resetFocusRequester).focusProperties { left = earlierFocusRequester; right = laterFocusRequester }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.primary, focusedContentColor = MaterialTheme.colorScheme.onPrimary)) {
                Box(contentAlignment = Alignment.Center) { Text(formatQuickMenuDelay(subtitleDelayMs), style = MaterialTheme.typography.titleLarge) }
            }
            QuickMenuAction("Later", Icons.Rounded.FastForward, onIncrease, focusRequester = laterFocusRequester, onClose = onClose, modifier = Modifier.weight(1f).focusProperties { left = resetFocusRequester; right = laterFocusRequester })
        }
        Text("Negative values show subtitles earlier.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuOpenSubtitlesPage(
    searchFocusRequester: FocusRequester,
    firstResultFocusRequester: FocusRequester,
    state: OnlineSubtitleState,
    session: OpenSubtitlesSessionState,
    onSearch: () -> Unit,
    onDownload: (OnlineSubtitleOption) -> Unit,
    onLeavePlayerToOpenSubtitles: () -> Unit,
    onClose: () -> Unit
) {
    var showLeavePlayerPrompt by remember { mutableStateOf(false) }
    val signedIn = session is OpenSubtitlesSessionState.SignedIn
    val hasResults = state is OnlineSubtitleState.Results && state.options.isNotEmpty()
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudDownload, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("OpenSubtitles", style = MaterialTheme.typography.headlineMedium)
        }
        Text(when (session) {
            is OpenSubtitlesSessionState.SignedIn -> "Signed in as ${session.username}"
            OpenSubtitlesSessionState.SigningIn -> "Signing in…"
            else -> "Sign in from Settings to search and download subtitles."
        }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val searchAction: () -> Unit = if (signedIn) onSearch else ({ showLeavePlayerPrompt = true })
        QuickMenuAction(
            if (signedIn && state is OnlineSubtitleState.Loading) "Searching…" else "Search for subtitles",
            Icons.Rounded.CloudDownload,
            searchAction,
            focusRequester = searchFocusRequester,
            onClose = onClose,
            closeOnUp = true,
            modifier = Modifier.focusProperties { down = if (hasResults) firstResultFocusRequester else searchFocusRequester }
        )
        when {
            !signedIn -> Unit
            state is OnlineSubtitleState.Results && state.options.isEmpty() -> QuickMenuEmptyMessage("No subtitles found.")
            state is OnlineSubtitleState.Downloading -> QuickMenuEmptyMessage("Downloading ${state.label}…")
            state is OnlineSubtitleState.Attached -> QuickMenuEmptyMessage("Attached ${state.label}")
            state is OnlineSubtitleState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            state is OnlineSubtitleState.Results -> {
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.options, key = { it.fileId }) { option ->
                        val index = state.options.indexOf(option)
                        Surface(onClick = { onDownload(option) }, modifier = Modifier.then(if (index == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier).focusProperties { if (index == 0) up = searchFocusRequester }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.primary, focusedContentColor = MaterialTheme.colorScheme.onPrimary)) {
                            Column(Modifier.padding(18.dp)) {
                                Text(option.label, style = MaterialTheme.typography.titleMedium)
                                option.language?.let { Text(it.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showLeavePlayerPrompt) {
        LeavePlayerForSubtitlesDialog(onDismiss = { showLeavePlayerPrompt = false }, onConfirm = { showLeavePlayerPrompt = false; onLeavePlayerToOpenSubtitles() })
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LeavePlayerForSubtitlesDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val cancelRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { cancelRequester.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.width(640.dp), shape = ApertureTheme.shapes.dialog, colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(ApertureTheme.spacing.huge), verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)) {
                Text("OpenSubtitles sign-in required", style = MaterialTheme.typography.headlineSmall)
                Text("You need to sign into OpenSubtitles to do that. Leave the player to sign in?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancelRequester)) { Text("Stay here") }
                    Button(onClick = onConfirm) { Text("Leave player") }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuEmptyMessage(message: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = 96.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuPlaybackPage(player: PlayerEngine, firstFocusRequester: FocusRequester, onClose: () -> Unit) {
    val isPlaying by player.isPlaying.collectAsState()
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(2) }) { Text("Playback", style = MaterialTheme.typography.headlineMedium) }
        item { QuickMenuAction("Restart", Icons.Rounded.Replay, { player.seekTo(0) }, focusRequester = firstFocusRequester, onClose = onClose, closeOnUp = true) }
        item { QuickMenuAction("Rewind 10 seconds", Icons.Rounded.FastRewind, { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }, onClose = onClose, closeOnUp = true) }
        item { QuickMenuAction(if (isPlaying) "Pause" else "Play", if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, { if (isPlaying) player.pause() else player.play() }, onClose = onClose) }
        item { QuickMenuAction("Forward 10 seconds", Icons.Rounded.FastForward, { val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE; player.seekTo((player.currentPosition + 10_000L).coerceAtMost(duration)) }, onClose = onClose) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuVideoPage(selectedResizeMode: Int, firstFocusRequester: FocusRequester, onSelected: (Int) -> Unit, onClose: () -> Unit) {
    val options = QuickMenuVideoResizeMode.entries
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AspectRatio, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Video", style = MaterialTheme.typography.headlineMedium)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEachIndexed { index, option ->
                Surface(onClick = { onSelected(option.media3Mode) }, modifier = Modifier.weight(1f).then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier).closeQuickMenuOnUp(onClose), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = if (option.media3Mode == selectedResizeMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.primary, focusedContentColor = MaterialTheme.colorScheme.onPrimary, pressedContainerColor = MaterialTheme.colorScheme.primary, pressedContentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(option.label, style = MaterialTheme.typography.titleMedium); Text(option.description) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuOtherPage(firstFocusRequester: FocusRequester, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Other", style = MaterialTheme.typography.headlineMedium)
        Box(Modifier.fillMaxSize().focusRequester(firstFocusRequester).focusable().closeQuickMenuOnUp(onClose), contentAlignment = Alignment.Center) {
            Text("Coming soon!", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, focusRequester: FocusRequester? = null, onClose: (() -> Unit)? = null, closeOnUp: Boolean = false, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).then(if (closeOnUp && onClose != null) Modifier.closeQuickMenuOnUp(onClose) else Modifier), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.primary, focusedContentColor = MaterialTheme.colorScheme.onPrimary, pressedContainerColor = MaterialTheme.colorScheme.primary, pressedContentColor = MaterialTheme.colorScheme.onPrimary)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private data class QuickMenuTrackItem(val name: String, val isSelected: Boolean, val isSupported: Boolean, val group: Tracks.Group, val index: Int)

private fun getQuickMenuTrackItems(tracks: Tracks, type: Int): List<QuickMenuTrackItem> {
    val result = mutableListOf<QuickMenuTrackItem>()
    tracks.groups.forEach { group ->
        if (group.type != type) return@forEach
        for (index in 0 until group.length) {
            val format = group.getTrackFormat(index)
            result += QuickMenuTrackItem(name = format.label ?: format.language?.uppercase() ?: when (type) {
                C.TRACK_TYPE_AUDIO -> "Audio ${index + 1}"
                C.TRACK_TYPE_TEXT -> "Subtitle ${index + 1}"
                else -> "Track ${index + 1}"
            }, isSelected = group.isTrackSelected(index), isSupported = group.isTrackSupported(index), group = group, index = index)
        }
    }
    return result
}
