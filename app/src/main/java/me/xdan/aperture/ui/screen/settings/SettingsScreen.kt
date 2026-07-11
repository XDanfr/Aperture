package me.xdan.aperture.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

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
    var showLicenses by remember { mutableStateOf(false) }
    var showSpotlightDaysPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val internalDonateFocusRequester = remember { FocusRequester() }
    val donateFocusRequester = if (restoreFocusKey == SETTINGS_DONATE_FOCUS_KEY) {
        contentEntryFocusRequester
    } else {
        internalDonateFocusRequester
    }

    LaunchedEffect(Unit) {
        val restoreIndex = when (restoreFocusKey) {
            SETTINGS_SPOTLIGHT_TOGGLE_FOCUS_KEY -> 1
            SETTINGS_SPOTLIGHT_DAYS_FOCUS_KEY -> 2
            SETTINGS_RESCAN_FOCUS_KEY -> 4
            SETTINGS_LICENCES_FOCUS_KEY -> 6
            SETTINGS_TMDB_FOCUS_KEY -> 7
            SETTINGS_CLEAR_CACHE_FOCUS_KEY -> 8
            SETTINGS_DONATE_FOCUS_KEY -> 9
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
                    title = "Hide finished media from Spotlight",
                    subtitle = if (spotlightSettings.hideFinishedFromSpotlight) {
                        "On · Completed titles return after ${spotlightSettings.exclusionDays} days"
                    } else {
                        "Off · Completed titles can appear immediately"
                    },
                    icon = Icons.Rounded.VisibilityOff,
                    drawerFocusRequester = drawerFocusRequester,
                    focusRequester = contentEntryFocusRequester.takeIf {
                        restoreFocusKey == null || restoreFocusKey == SETTINGS_SPOTLIGHT_TOGGLE_FOCUS_KEY
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
                    title = "Force Rescan Local Files",
                    subtitle = "Update the library from storage",
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
                    subtitle = "Size, colour and opacity · Coming soon",
                    icon = Icons.Rounded.Subtitles,
                    enabled = false,
                    drawerFocusRequester = drawerFocusRequester,
                    onFocused = { requester ->
                        onFocusKeyChanged(SETTINGS_SUBTITLES_FOCUS_KEY)
                        onContentFocused(requester)
                    },
                    onClick = {}
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
}

private const val SETTINGS_LANGUAGE_FOCUS_KEY = "language"
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
