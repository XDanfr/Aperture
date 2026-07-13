package me.xdan.aperture.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import me.xdan.aperture.ui.theme.ApertureThemeOptions
import me.xdan.aperture.domain.repository.MediaFolder

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    drawerFocusRequester: FocusRequester? = null,
    contentEntryFocusRequester: FocusRequester = FocusRequester.Default,
    restoreFocusKey: String? = null,
    onFocusKeyChanged: (String) -> Unit = {},
    onContentFocused: (FocusRequester) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val spotlightSettings by viewModel.spotlightSettings.collectAsState()
    val selectedThemeId by viewModel.themeId.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val hiddenMedia by viewModel.hiddenMedia.collectAsState()
    val mediaFolders by viewModel.mediaFolders.collectAsState()
    val mediaFolderMessage by viewModel.mediaFolderMessage.collectAsState()
    val showPresentationMode by viewModel.showPresentationMode.collectAsState()
    val subtitleAppearance by viewModel.subtitleAppearance.collectAsState()
    var showLicenses by remember { mutableStateOf(false) }
    var showThemes by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showHiddenMedia by remember { mutableStateOf(false) }
    var showSpotlightDaysPicker by remember { mutableStateOf(false) }
    var showShowLayoutPicker by remember { mutableStateOf(false) }
    var showSubtitleAppearance by remember { mutableStateOf(false) }
    var showMediaFolders by remember { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let(viewModel::addMediaFolder)
    }
    val listState = rememberLazyListState()
    val internalDonateFocusRequester = remember { FocusRequester() }
    val donateFocusRequester = if (restoreFocusKey == SETTINGS_DONATE_FOCUS_KEY) {
        contentEntryFocusRequester
    } else {
        internalDonateFocusRequester
    }

    LaunchedEffect(Unit) {
        val restoreIndex = when (restoreFocusKey) {
            SETTINGS_THEME_FOCUS_KEY -> 1
            SETTINGS_SHOW_LAYOUT_FOCUS_KEY -> 2
            SETTINGS_SPOTLIGHT_TOGGLE_FOCUS_KEY -> 3
            SETTINGS_SPOTLIGHT_DAYS_FOCUS_KEY -> 4
            SETTINGS_MEDIA_FOLDERS_FOCUS_KEY -> 7
            SETTINGS_RESCAN_FOCUS_KEY -> 8
            SETTINGS_SUBTITLES_FOCUS_KEY -> 9
            SETTINGS_LICENCES_FOCUS_KEY -> 10
            SETTINGS_UPDATE_FOCUS_KEY -> 11
            SETTINGS_TMDB_FOCUS_KEY -> 12
            SETTINGS_CLEAR_CACHE_FOCUS_KEY -> 13
            SETTINGS_DONATE_FOCUS_KEY -> 14
            else -> 0
        }
        if (restoreIndex > 0) listState.scrollToItem(restoreIndex)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1_000.dp)
                .padding(horizontal = 72.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(bottom = 26.dp)
                )
            }

            item {
                SettingsItem(
                    title = "Theme",
                    subtitle = ApertureThemeOptions.firstOrNull { it.id == selectedThemeId }?.label
                        ?: "Aperture Purple",
                    icon = Icons.Rounded.Palette,
                    drawerFocusRequester = drawerFocusRequester,
                    focusRequester = contentEntryFocusRequester.takeIf {
                        restoreFocusKey == null || restoreFocusKey == SETTINGS_THEME_FOCUS_KEY
                    },
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_THEME_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = { showThemes = true }
                )
            }

            item {
                SettingsItem(
                    title = "TV show layout",
                    subtitle = if (showPresentationMode == "grouped") {
                        "One card per show with a season and episode selector"
                    } else {
                        "Show every episode in season rows"
                    },
                    icon = Icons.Rounded.ViewModule,
                    drawerFocusRequester = drawerFocusRequester,
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_SHOW_LAYOUT_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = { showShowLayoutPicker = true }
                )
            }

            item {
                SettingsItem(
                    title = "Hide finished media from Spotlight",
                    subtitle = if (spotlightSettings.hideFinishedFromSpotlight) {
                        "On · Completed titles return after ${spotlightSettings.exclusionDays} days"
                    } else {
                        "Off · Completed titles can appear immediately"
                    },
                    icon = Icons.Rounded.VisibilityOff,
                    drawerFocusRequester = drawerFocusRequester,
                    focusRequester = contentEntryFocusRequester.takeIf {
                        restoreFocusKey == SETTINGS_SPOTLIGHT_TOGGLE_FOCUS_KEY
                    },
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_SPOTLIGHT_TOGGLE_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = {
                        viewModel.setHideFinishedFromSpotlight(
                            !spotlightSettings.hideFinishedFromSpotlight
                        )
                    }
                )
            }

            item {
                SettingsItem(
                    title = "Finished Spotlight exclusion period",
                    subtitle = "${spotlightSettings.exclusionDays} days",
                    icon = Icons.Rounded.DateRange,
                    enabled = spotlightSettings.hideFinishedFromSpotlight,
                    drawerFocusRequester = drawerFocusRequester,
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_SPOTLIGHT_DAYS_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = { showSpotlightDaysPicker = true }
                )
            }

            item {
                SettingsItem(
                    title = "Language",
                    subtitle = "English (system default) · More options coming soon",
                    icon = Icons.Rounded.Language,
                    enabled = false,
                    drawerFocusRequester = drawerFocusRequester,
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_LANGUAGE_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = {}
                )
            }

            item {
                SettingsItem(
                    title = "Hidden Media",
                    subtitle = if (hiddenMedia.isEmpty()) "No hidden titles" else "${hiddenMedia.size} hidden titles",
                    icon = Icons.Rounded.Visibility,
                    drawerFocusRequester = drawerFocusRequester,
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_HIDDEN_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = { showHiddenMedia = true }
                )
            }

            item {
                SettingsItem(
                    title = "Media folders",
                    subtitle = when {
                        mediaFolderMessage != null -> mediaFolderMessage!!
                        mediaFolders.isEmpty() -> "Add a USB drive or another media location"
                        mediaFolders.size == 1 -> mediaFolders.first().name
                        else -> "${mediaFolders.size} selected folders"
                    },
                    icon = Icons.Rounded.FolderOpen,
                    drawerFocusRequester = drawerFocusRequester,
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_MEDIA_FOLDERS_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = { showMediaFolders = true }
                )
            }

            item {
                SettingsItem(
                    title = "Force Rescan Local Files",
                    subtitle = "Update the library from device storage and selected folders",
                    icon = Icons.Rounded.Refresh,
                    drawerFocusRequester = drawerFocusRequester,
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_RESCAN_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = viewModel::forceRescan
                )
            }

            item {
                SettingsItem(
                    title = "Subtitle Appearance",
                    subtitle = "${(subtitleAppearance.textScale * 100).toInt()}% size · ${subtitleAppearance.colour.replaceFirstChar { it.uppercase() }} · ${(subtitleAppearance.backgroundOpacity * 100).toInt()}% background",
                    icon = Icons.Rounded.Subtitles,
                    drawerFocusRequester = drawerFocusRequester,
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_SUBTITLES_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = { showSubtitleAppearance = true }
                )
            }

            item {
                SettingsItem(
                    title = "Open Source Licences",
                    subtitle = "Libraries and projects used by Aperture",
                    icon = Icons.Rounded.Info,
                    drawerFocusRequester = drawerFocusRequester,
                    focusRequester = contentEntryFocusRequester.takeIf {
                        restoreFocusKey == SETTINGS_LICENCES_FOCUS_KEY
                    },
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_LICENCES_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = { showLicenses = true }
                )
            }

            item {
                SettingsItem(
                    title = "Check for Updates",
                    subtitle = when (val update = updateState) {
                        UpdateCheckState.Checking -> "Checking GitHub…"
                        is UpdateCheckState.Available -> "${update.version} is available"
                        is UpdateCheckState.PermissionRequired -> "Allow APK installation to continue"
                        is UpdateCheckState.Downloading -> "Downloading ${update.update.version} · ${(update.progress * 100).toInt()}%"
                        is UpdateCheckState.Installing -> "Opening the ${update.version} installer…"
                        is UpdateCheckState.Current -> "Up to date (${update.version})"
                        is UpdateCheckState.Error -> update.message
                        UpdateCheckState.Idle -> "Compare this build with the latest GitHub release"
                    },
                    icon = Icons.Rounded.SystemUpdate,
                    drawerFocusRequester = drawerFocusRequester,
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_UPDATE_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = {
                        showUpdateDialog = true
                        viewModel.checkForUpdates()
                    }
                )
            }

            item {
                SettingsItem(
                    title = "The Movie Database (TMDB)",
                    subtitle = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                    icon = Icons.Rounded.Movie,
                    drawerFocusRequester = drawerFocusRequester,
                    focusRequester = contentEntryFocusRequester.takeIf {
                        restoreFocusKey == SETTINGS_TMDB_FOCUS_KEY
                    },
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_TMDB_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = { uriHandler.openUri("https://www.themoviedb.org") }
                )
            }

            item {
                SettingsItem(
                    title = "Clear Artwork Cache",
                    subtitle = "Remove downloaded posters and backdrops",
                    icon = Icons.Rounded.DeleteSweep,
                    drawerFocusRequester = drawerFocusRequester,
                    focusRequester = contentEntryFocusRequester.takeIf {
                        restoreFocusKey == SETTINGS_CLEAR_CACHE_FOCUS_KEY
                    },
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_CLEAR_CACHE_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = viewModel::clearCache
                )
            }

            item {
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = { uriHandler.openUri("https://github.com/XDanfr") },
                    modifier = Modifier
                        .padding(horizontal = 56.dp)
                        .fillMaxWidth()
                        .then(
                            if (drawerFocusRequester != null) {
                                Modifier.focusProperties { left = drawerFocusRequester }
                            } else Modifier
                        )
                        .focusRequester(donateFocusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                onFocusKeyChanged(SETTINGS_DONATE_FOCUS_KEY)
                                onContentFocused(donateFocusRequester)
                            }
                        }
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Donate to XDanfr on GitHub")
                }
            }
        }
    }

    if (showLicenses) {
        LicencesDialog(onDismiss = { showLicenses = false })
    }
    if (showThemes) {
        ThemePickerDialog(
            selectedThemeId = selectedThemeId,
            onSelect = viewModel::setTheme,
            onDismiss = { showThemes = false }
        )
    }
    if (showUpdateDialog) {
        UpdateDialog(
            state = updateState,
            onInstall = viewModel::downloadAndInstall,
            onDismiss = { showUpdateDialog = false }
        )
    }
    if (showHiddenMedia) {
        HiddenMediaDialog(
            media = hiddenMedia,
            onUnhide = viewModel::unhide,
            onDismiss = { showHiddenMedia = false }
        )
    }
    if (showSpotlightDaysPicker) {
        SpotlightDaysDialog(
            initialDays = spotlightSettings.exclusionDays,
            onSave = {
                viewModel.setFinishedSpotlightExclusionDays(it)
                showSpotlightDaysPicker = false
            },
            onDismiss = { showSpotlightDaysPicker = false }
        )
    }
    if (showShowLayoutPicker) {
        ShowLayoutDialog(
            selectedMode = showPresentationMode,
            onSelect = {
                viewModel.setShowPresentationMode(it)
                showShowLayoutPicker = false
            },
            onDismiss = { showShowLayoutPicker = false }
        )
    }
    if (showSubtitleAppearance) {
        SubtitleAppearanceDialog(
            initial = subtitleAppearance,
            onSave = {
                viewModel.setSubtitleAppearance(it)
                showSubtitleAppearance = false
            },
            onDismiss = { showSubtitleAppearance = false }
        )
    }
    if (showMediaFolders) {
        MediaFoldersDialog(
            folders = mediaFolders,
            message = mediaFolderMessage,
            onAdd = { folderPicker.launch(null) },
            onRemove = viewModel::removeMediaFolder,
            onDismiss = { showMediaFolders = false }
        )
    }
}

private const val SETTINGS_LANGUAGE_FOCUS_KEY = "language"
private const val SETTINGS_THEME_FOCUS_KEY = "theme"
private const val SETTINGS_SHOW_LAYOUT_FOCUS_KEY = "show_layout"
private const val SETTINGS_UPDATE_FOCUS_KEY = "update"
private const val SETTINGS_HIDDEN_FOCUS_KEY = "hidden"
private const val SETTINGS_MEDIA_FOLDERS_FOCUS_KEY = "media_folders"
private const val SETTINGS_RESCAN_FOCUS_KEY = "rescan"
private const val SETTINGS_SUBTITLES_FOCUS_KEY = "subtitles"
private const val SETTINGS_SPOTLIGHT_TOGGLE_FOCUS_KEY = "spotlight_toggle"
private const val SETTINGS_SPOTLIGHT_DAYS_FOCUS_KEY = "spotlight_days"
private const val SETTINGS_LICENCES_FOCUS_KEY = "licences"
private const val SETTINGS_TMDB_FOCUS_KEY = "tmdb"
private const val SETTINGS_CLEAR_CACHE_FOCUS_KEY = "clear_cache"
private const val SETTINGS_DONATE_FOCUS_KEY = "donate"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediaFoldersDialog(
    folders: List<MediaFolder>,
    message: String?,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val addFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        addFocusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(760.dp),
                shape = RoundedCornerShape(28.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(38.dp)) {
                    Text("Media folders", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Choose folders on USB drives or other storage. Aperture keeps read access after a restart.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                    if (message != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(message, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(22.dp))

                    if (folders.isEmpty()) {
                        Text(
                            "No extra folders selected. Internal media scanning still works as before.",
                            modifier = Modifier.padding(vertical = 24.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 330.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(folders, key = { it.uri }) { folder ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = SurfaceDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.width(500.dp)) {
                                            Text(folder.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                if (folder.isAvailable) "Available" else "Drive unavailable",
                                                color = if (folder.isAvailable) {
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                }
                                            )
                                        }
                                        OutlinedButton(onClick = { onRemove(folder.uri) }) {
                                            Text("Remove")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onAdd,
                            modifier = Modifier.focusRequester(addFocusRequester)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add folder")
                        }
                        OutlinedButton(onClick = onDismiss) { Text("Done") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowLayoutDialog(
    selectedMode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { firstRequester.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.width(560.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("TV show layout", style = MaterialTheme.typography.headlineSmall)
                Text("You can switch layouts without rescanning your library.")
                listOf(
                    "grouped" to ("Grouped shows" to "One poster per show; choose a season and episode in its popup."),
                    "episodes" to ("Episode rows" to "The current layout, with every season and episode shown on the page.")
                ).forEachIndexed { index, (mode, copy) ->
                    Surface(
                        onClick = { onSelect(mode) },
                        modifier = Modifier.fillMaxWidth().then(
                            if (index == 0) Modifier.focusRequester(firstRequester) else Modifier
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (mode == selectedMode) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(copy.first, style = MaterialTheme.typography.titleMedium)
                            Text(copy.second, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun SubtitleAppearanceDialog(
    initial: SubtitleAppearanceSettings,
    onSave: (SubtitleAppearanceSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var settings by remember(initial) { mutableStateOf(initial) }
    val firstRequester = remember { FocusRequester() }
    val colours = listOf("white", "yellow", "cyan")
    LaunchedEffect(Unit) { delay(80); runCatching { firstRequester.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.width(620.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(34.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Text("Subtitle Appearance", style = MaterialTheme.typography.headlineSmall)
                Text("Aperture uses a readable system sans-serif typeface with a translucent dark background.")
                PreferenceStepper(
                    title = "Text size",
                    value = "${(settings.textScale * 100).toInt()}%",
                    onDecrease = { settings = settings.copy(textScale = (settings.textScale - 0.1f).coerceAtLeast(0.7f)) },
                    onIncrease = { settings = settings.copy(textScale = (settings.textScale + 0.1f).coerceAtMost(1.6f)) },
                    focusRequester = firstRequester
                )
                PreferenceStepper(
                    title = "Background opacity",
                    value = "${(settings.backgroundOpacity * 100).toInt()}%",
                    onDecrease = { settings = settings.copy(backgroundOpacity = (settings.backgroundOpacity - 0.1f).coerceAtLeast(0f)) },
                    onIncrease = { settings = settings.copy(backgroundOpacity = (settings.backgroundOpacity + 0.1f).coerceAtMost(0.9f)) }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Text colour", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = {
                        val next = (colours.indexOf(settings.colour).coerceAtLeast(0) + 1) % colours.size
                        settings = settings.copy(colour = colours[next])
                    }) { Text(settings.colour.replaceFirstChar { it.uppercase() }) }
                }
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(settings) }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun PreferenceStepper(
    title: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = onDecrease,
            modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
        ) { Text("−") }
        Text(value, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onIncrease) { Text("+") }
    }
}

@Composable
private fun HiddenMediaDialog(
    media: List<me.xdan.aperture.data.local.entity.MediaEntity>,
    onUnhide: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.width(660.dp).height(560.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(30.dp)) {
                Text("Hidden Media", style = MaterialTheme.typography.headlineSmall)
                Text("Hidden titles stay out of Home, Search, Movies, Shows and My List.")
                Spacer(Modifier.height(18.dp))
                if (media.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Nothing is hidden.")
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(media, key = { it.id }) { item ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Button(onClick = { onUnhide(item.id) }) { Text("Unhide") }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    state: UpdateCheckState,
    onInstall: (UpdateCheckState.Available) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.width(500.dp).padding(32.dp)) {
                Text("Aperture updates", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(18.dp))
                Text(
                    when (state) {
                        UpdateCheckState.Idle, UpdateCheckState.Checking -> "Checking the latest GitHub release…"
                        is UpdateCheckState.Current -> "You're up to date on ${state.version}."
                        is UpdateCheckState.Available -> if (state.apkUrl != null) {
                            "${state.version} is available and can be installed directly."
                        } else {
                            "${state.version} is available, but this release has no APK asset."
                        }
                        is UpdateCheckState.PermissionRequired -> "Allow Aperture to install unknown apps, return here, then continue."
                        is UpdateCheckState.Downloading -> "Downloading ${state.update.version}… ${(state.progress * 100).toInt()}%"
                        is UpdateCheckState.Installing -> "Opening Android's installer for ${state.version}…"
                        is UpdateCheckState.Error -> state.message
                    }
                )
                if (state is UpdateCheckState.Downloading) {
                    Spacer(Modifier.height(14.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.tv.material3.OutlinedButton(onClick = onDismiss) { Text("Close") }
                    if (state is UpdateCheckState.Available) {
                        Button(onClick = { onInstall(state) }) {
                            Text(if (state.apkUrl != null) "Download & install" else "Open release")
                        }
                    }
                    if (state is UpdateCheckState.PermissionRequired) {
                        Button(onClick = { onInstall(state.update) }) { Text("Continue") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePickerDialog(
    selectedThemeId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { firstRequester.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(620.dp).heightIn(max = 620.dp),
                shape = RoundedCornerShape(24.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(32.dp)) {
                    Text("Choose a theme", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "The preview updates Aperture, the player OSD and Quick Menu immediately.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                    Spacer(Modifier.height(20.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        itemsIndexed(ApertureThemeOptions, key = { _, option -> option.id }) { index, option ->
                            Surface(
                                onClick = { onSelect(option.id) },
                                modifier = Modifier.fillMaxWidth().then(
                                    if (index == 0) Modifier.focusRequester(firstRequester) else Modifier
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (option.id == selectedThemeId) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(28.dp).background(option.preview, CircleShape))
                                    Spacer(Modifier.width(14.dp))
                                    Text(option.label, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.weight(1f))
                                    if (option.id == selectedThemeId) Text("Selected")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    drawerFocusRequester: FocusRequester? = null,
    focusRequester: FocusRequester? = null,
    onFocused: (FocusRequester) -> Unit = {}
) {
    val internalFocusRequester = remember { FocusRequester() }
    val itemFocusRequester = focusRequester
        ?.takeUnless { it == FocusRequester.Default }
        ?: internalFocusRequester

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (drawerFocusRequester != null) {
                    Modifier.focusProperties { left = drawerFocusRequester }
                } else Modifier
            )
            .focusRequester(itemFocusRequester)
            .onFocusChanged {
                if (it.isFocused) onFocused(itemFocusRequester)
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1.015f,
            pressedScale = 0.99f
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(14.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(18.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(if (enabled) 0.72f else 0.48f)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LicencesDialog(onDismiss: () -> Unit) {
    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        closeFocusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(560.dp),
                shape = RoundedCornerShape(24.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(38.dp)) {
                    Text("Open Source Licences", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(22.dp))
                    Text("• Nova Video Player (Archos)")
                    Text("• laposa/media-player")
                    Text("• AndroidX Media3")
                    Text("• NextLib Media3 FFmpeg extensions (GPLv3)")
                    Text("• FFmpeg")
                    Text("• Jetpack Compose")
                    Text("• Coil")
                    Text("• Retrofit, OkHttp and Moshi")
                    Spacer(Modifier.height(30.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.End)
                            .focusRequester(closeFocusRequester)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SpotlightDaysDialog(
    initialDays: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var days by remember(initialDays) { mutableStateOf(initialDays.coerceIn(1, 365)) }
    val decreaseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        decreaseFocusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(520.dp),
                shape = RoundedCornerShape(24.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(38.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Finished Spotlight exclusion", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Choose how long completed titles stay out of Spotlight.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                    Spacer(Modifier.height(30.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Button(
                            onClick = { days = (days - 1).coerceAtLeast(1) },
                            modifier = Modifier.focusRequester(decreaseFocusRequester)
                        ) { Text("−") }
                        Text("$days days", style = MaterialTheme.typography.displaySmall)
                        Button(onClick = { days = (days + 1).coerceAtMost(365) }) { Text("+") }
                    }
                    Spacer(Modifier.height(32.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        androidx.tv.material3.OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                        Button(
                            onClick = { onSave(days) }
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}
