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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Subtitles
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
import me.xdan.aperture.ui.screen.settings.SettingsViewModel
import me.xdan.aperture.ui.screen.settings.SubtitleAppearanceDialog
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
    val settingsViewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
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
                    focusRequesters = categoryFocusRequesters,
                    onSelected = { index, selectedPage ->
                        selectedCategoryIndex = index
                        page = selectedPage
                    },
                    onClose = onClose
                )
                QuickMenuPage.Audio -> QuickMenuTrackPage(
                    title = "Audio",
                    type = C.TRACK_TYPE_AUDIO,
                    player = player,
                    firstFocusRequester = pageFocusRequester,
                    emptyLabel = "No audio tracks available.",
                    onClose = onClose
                )
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
                    onSaveSubtitleAppearance = { settings ->
                        playerViewModel.setSubtitleAppearance(
                            textScale = settings.textScale,
                            colour = settings.colour,
                            backgroundOpacity = settings.backgroundOpacity
                        )
                    },
                    onSearchOnline = playerViewModel::searchOpenSubtitles,
                    onDownloadOnline = playerViewModel::downloadOpenSubtitle,
                    onLeavePlayerToOpenSubtitles = onLeavePlayerToOpenSubtitles
                )
                QuickMenuPage.Playback -> QuickMenuPlaybackPage(
                    player = player,
                    firstFocusRequester = pageFocusRequester,
                    onClose = onClose
                )
                QuickMenuPage.Video -> QuickMenuVideoPage(
                    selectedResizeMode = videoResizeMode,
                    firstFocusRequester = pageFocusRequester,
                    onSelected = onVideoResizeModeSelected,
                    onClose = onClose
                )
                QuickMenuPage.Other -> QuickMenuOtherPage(
                    firstFocusRequester = pageFocusRequester,
                    onClose = onClose
                )
            }
        }
    )
}

private fun Modifier.closeQuickMenuOnUp(onClose: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (
            event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
            event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
        ) {
            onClose()
            true
        } else {
            false
        }
    }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuCategories(
    focusRequesters: List<FocusRequester>,
    onSelected: (Int, QuickMenuPage) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Quick Menu", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Choose a category",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            quickMenuCategories.forEachIndexed { index, category ->
                Surface(
                    onClick = { onSelected(index, category.page) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequesters[index])
                        .closeQuickMenuOnUp(onClose)
                        .focusProperties {
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
private fun QuickMenuTrackPage(
    title: String,
    type: Int,
    player: androidx.media3.common.Player,
    firstFocusRequester: FocusRequester,
    emptyLabel: String,
    onClose: () -> Unit
) {
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) { tracks = newTracks }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val items = getQuickMenuTrackItems(tracks, type).filter { it.isSupported }
    val selectedItem = items.firstOrNull { it.isSelected }
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (items.isEmpty()) {
            QuickMenuEmptyMessage(emptyLabel)
            return
        }
        Text(
            "Selected: ${selectedItem?.name ?: "None"}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
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
                            .setOverrideForType(TrackSelectionOverride(item.group.mediaTrackGroup, item.index))
                            .build()
                    },
                    modifier = Modifier
                        .then(if (item == items.firstOrNull()) Modifier.focusRequester(firstFocusRequester) else Modifier)
                        .then(if (items.indexOf(item) < 3) Modifier.closeQuickMenuOnUp(onClose) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (item.isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                        pressedContainerColor = MaterialTheme.colorScheme.primary,
                        pressedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(item.name, modifier = Modifier.padding(18.dp), maxLines = 2)
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
    var subPage by remember { mutableStateOf(QuickMenuSubtitleSubPage.MAIN) }
    var showCustomise by remember { mutableStateOf(false) }

    BackHandler(enabled = subPage != QuickMenuSubtitleSubPage.MAIN) {
        subPage = QuickMenuSubtitleSubPage.MAIN
    }

    AnimatedContent(
        targetState = subPage,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 5 },
                initialContentExit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 7 }
            )
        },
        label = "quickMenuSubtitlePageTransition"
    ) { currentPage ->
        when (currentPage) {
            QuickMenuSubtitleSubPage.MAIN -> QuickMenuSubtitlesMainPage(
                player = player,
                firstFocusRequester = firstFocusRequester,
                onSync = { subPage = QuickMenuSubtitleSubPage.SYNC },
                onCustomise = { showCustomise = true },
                onOpenSubtitles = { subPage = QuickMenuSubtitleSubPage.OPENSUBTITLES },
                onClose = onClose
            )
            QuickMenuSubtitleSubPage.SYNC -> QuickMenuSubtitleSyncPage(
                firstFocusRequester = firstFocusRequester,
                subtitleDelayMs = subtitleDelayMs,
                onDecrease = onSubtitleDelayDecrease,
                onIncrease = onSubtitleDelayIncrease,
                onReset = onSubtitleDelayReset,
                onClose = onClose
            )
            QuickMenuSubtitleSubPage.OPENSUBTITLES -> QuickMenuOpenSubtitlesPage(
                firstFocusRequester = firstFocusRequester,
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
        SubtitleAppearanceDialog(
            initial = subtitleAppearance,
            onSave = {
                onSaveSubtitleAppearance(it)
                showCustomise = false
            },
            onDismiss = { showCustomise = false }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuSubtitlesMainPage(
    player: androidx.media3.common.Player,
    firstFocusRequester: FocusRequester,
    onSync: () -> Unit,
    onCustomise: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onClose: () -> Unit
) {
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) { tracks = newTracks }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val items = getQuickMenuTrackItems(tracks, C.TRACK_TYPE_TEXT).filter { it.isSupported }
    val selectedItem = items.firstOrNull { it.isSelected }
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Subtitles", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Selected: ${selectedItem?.name ?: "None"}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickMenuAction("Sync", Icons.Rounded.Sync, onSync, firstFocusRequester, onClose, true)
            QuickMenuAction("Customise", Icons.Rounded.FormatColorText, onCustomise, onClose = onClose, closeOnUp = true)
            QuickMenuAction("OpenSubtitles", Icons.Rounded.CloudDownload, onOpenSubtitles, onClose = onClose, closeOnUp = true)
        }
        if (items.isEmpty()) {
            QuickMenuEmptyMessage("No subtitle tracks available.")
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { "${it.group.mediaTrackGroup.id}-${it.index}" }) { item ->
                Surface(
                    onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(item.group.mediaTrackGroup, item.index))
                            .build()
                    },
                    modifier = Modifier
                        .then(if (item == items.firstOrNull()) Modifier.focusRequester(firstFocusRequester) else Modifier)
                        .then(if (items.indexOf(item) < 3) Modifier.closeQuickMenuOnUp(onClose) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (item.isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                        pressedContainerColor = MaterialTheme.colorScheme.primary,
                        pressedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(item.name, modifier = Modifier.padding(18.dp), maxLines = 2)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuSubtitleSyncPage(
    firstFocusRequester: FocusRequester,
    subtitleDelayMs: Long,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Subtitle sync", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Adjust the subtitle timing for this video.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth().height(110.dp).focusRequester(firstFocusRequester).closeQuickMenuOnUp(onClose),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                focusedContentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(formatQuickMenuDelay(subtitleDelayMs), style = MaterialTheme.typography.headlineSmall)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickMenuAction("Earlier", Icons.Rounded.FastRewind, onDecrease, onClose = onClose)
            QuickMenuAction("Later", Icons.Rounded.FastForward, onIncrease, onClose = onClose)
        }
        Text(
            "Negative values show subtitles earlier.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuOpenSubtitlesPage(
    firstFocusRequester: FocusRequester,
    state: OnlineSubtitleState,
    session: OpenSubtitlesSessionState,
    onSearch: () -> Unit,
    onDownload: (OnlineSubtitleOption) -> Unit,
    onLeavePlayerToOpenSubtitles: () -> Unit,
    onClose: () -> Unit
) {
    var showLeavePlayerPrompt by remember { mutableStateOf(false) }
    val signedIn = session is OpenSubtitlesSessionState.SignedIn

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudDownload, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("OpenSubtitles", style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            when (session) {
                is OpenSubtitlesSessionState.SignedIn -> "Signed in as ${session.username}"
                OpenSubtitlesSessionState.SigningIn -> "Signing in…"
                else -> "Sign in from Settings to search and download subtitles."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!signedIn) {
            QuickMenuAction(
                "Search for subtitles",
                Icons.Rounded.CloudDownload,
                { showLeavePlayerPrompt = true },
                firstFocusRequester,
                onClose,
                true
            )
        } else {
            QuickMenuAction(
                if (state is OnlineSubtitleState.Loading) "Searching…" else "Search for subtitles",
                Icons.Rounded.CloudDownload,
                onSearch,
                firstFocusRequester,
                onClose,
                true
            )
            when (state) {
                is OnlineSubtitleState.Results -> {
                    if (state.options.isEmpty()) {
                        QuickMenuEmptyMessage("No subtitles found.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.options, key = { it.fileId }) { option ->
                                Surface(
                                    onClick = { onDownload(option) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                                        focusedContentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Column(Modifier.padding(18.dp)) {
                                        Text(option.label, style = MaterialTheme.typography.titleMedium)
                                        option.language?.let {
                                            Text(
                                                it.uppercase(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is OnlineSubtitleState.Downloading -> QuickMenuEmptyMessage("Downloading ${state.label}…")
                is OnlineSubtitleState.Attached -> QuickMenuEmptyMessage("Attached ${state.label}")
                is OnlineSubtitleState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }
    }

    if (showLeavePlayerPrompt) {
        LeavePlayerForSubtitlesDialog(
            onDismiss = { showLeavePlayerPrompt = false },
            onConfirm = {
                showLeavePlayerPrompt = false
                onLeavePlayerToOpenSubtitles()
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LeavePlayerForSubtitlesDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val cancelRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { cancelRequester.requestFocus() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.width(640.dp),
            shape = ApertureTheme.shapes.dialog,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier.padding(ApertureTheme.spacing.huge),
                verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)
            ) {
                Text("OpenSubtitles sign-in required", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "You need to sign into OpenSubtitles to do that. Leave the player to sign in?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancelRequester)) {
                        Text("Stay here")
                    }
                    Button(onClick = onConfirm) { Text("Leave player") }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuEmptyMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuPlaybackPage(
    player: androidx.media3.common.Player,
    firstFocusRequester: FocusRequester,
    onClose: () -> Unit
) {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Text("Playback", style = MaterialTheme.typography.headlineMedium)
        }
        item { QuickMenuAction("Restart", Icons.Rounded.Replay, { player.seekTo(0) }, firstFocusRequester, onClose, true) }
        item {
            QuickMenuAction(
                "Rewind 10 seconds",
                Icons.Rounded.FastRewind,
                { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) },
                onClose = onClose,
                closeOnUp = true
            )
        }
        item {
            QuickMenuAction(
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                { if (player.isPlaying) player.pause() else player.play() },
                onClose = onClose
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
                onClose = onClose
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickMenuVideoPage(
    selectedResizeMode: Int,
    firstFocusRequester: FocusRequester,
    onSelected: (Int) -> Unit,
    onClose: () -> Unit
) {
    val options = QuickMenuVideoResizeMode.entries
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AspectRatio, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Video", style = MaterialTheme.typography.headlineMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.forEachIndexed { index, option ->
                Surface(
                    onClick = { onSelected(option.media3Mode) },
                    modifier = Modifier
                        .weight(1f)
                        .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier)
                        .closeQuickMenuOnUp(onClose),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (option.media3Mode == selectedResizeMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                        pressedContainerColor = MaterialTheme.colorScheme.primary,
                        pressedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun QuickMenuOtherPage(firstFocusRequester: FocusRequester, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Other", style = MaterialTheme.typography.headlineMedium)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(firstFocusRequester)
                .focusable()
                .closeQuickMenuOnUp(onClose),
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
    onClose: (() -> Unit)? = null,
    closeOnUp: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (closeOnUp && onClose != null) Modifier.closeQuickMenuOnUp(onClose) else Modifier),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            pressedContainerColor = MaterialTheme.colorScheme.primary,
            pressedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
private fun formatQuickMenuDelay(delayMs: Long): String = when {
    delayMs == 0L -> "0 ms"
    kotlin.math.abs(delayMs) < 1_000L -> String.format(Locale.getDefault(), "%+d ms", delayMs)
    else -> String.format(Locale.getDefault(), "%+.1f s", delayMs / 1_000f)
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
