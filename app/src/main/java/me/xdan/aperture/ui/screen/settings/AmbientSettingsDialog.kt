@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package me.xdan.aperture.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import androidx.compose.ui.platform.LocalWindowInfo
import me.xdan.aperture.domain.model.AmbientBrandPlacement
import me.xdan.aperture.domain.model.AmbientModeType
import me.xdan.aperture.domain.model.AmbientSettings
import me.xdan.aperture.ui.component.AmbientSettingsPreview
import me.xdan.aperture.ui.component.expressive.ExpressiveToggle
import me.xdan.aperture.ui.theme.ApertureTheme

@Composable
internal fun AmbientSettingsDialog(
    initial: AmbientSettings,
    onSave: (AmbientSettings) -> Unit,
    onPreview: ((AmbientSettings) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var settings by remember(initial) { mutableStateOf(initial) }
    val firstRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val windowInfo = LocalWindowInfo.current
        LaunchedEffect(windowInfo.isWindowFocused) {
            if (!windowInfo.isWindowFocused) return@LaunchedEffect
            withFrameNanos { }
            withFrameNanos { }
            firstRequester.requestFocus()
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.width(820.dp).heightIn(max = 480.dp),
                shape = ApertureTheme.shapes.dialog,
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    Modifier.padding(ApertureTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)
                ) {
                    Text("Ambient mode", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Choose what Aperture shows after five minutes without input.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small)) {
                            AmbientSettingsPreview(settings, Modifier.fillMaxWidth())
                            Text("Preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(
                            Modifier.weight(1.35f).verticalScroll(rememberScrollState()).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small)
                        ) {
                            Text("Style", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small)) {
                                AmbientChoice(
                                    "Cinematic", settings.mode == AmbientModeType.CINEMATIC,
                                    { settings = settings.copy(mode = AmbientModeType.CINEMATIC) },
                                    Modifier.weight(1f).focusRequester(firstRequester)
                                )
                                AmbientChoice(
                                    "Poster wall", settings.mode == AmbientModeType.POSTER_WALL,
                                    { settings = settings.copy(mode = AmbientModeType.POSTER_WALL) }, Modifier.weight(1f)
                                )
                            }
                            if (settings.mode == AmbientModeType.POSTER_WALL) {
                                Text("Brand placement", style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small)) {
                                    AmbientChoice(
                                        "Top left logo", settings.wallBrandPlacement == AmbientBrandPlacement.TOP_LEFT,
                                        { settings = settings.copy(wallBrandPlacement = AmbientBrandPlacement.TOP_LEFT) }, Modifier.weight(1f)
                                    )
                                    AmbientChoice(
                                        "Centre icon", settings.wallBrandPlacement == AmbientBrandPlacement.CENTRE,
                                        { settings = settings.copy(wallBrandPlacement = AmbientBrandPlacement.CENTRE) }, Modifier.weight(1f)
                                    )
                                }
                            }
                            var isClockFocused by remember { mutableStateOf(false) }
                            Surface(
                                onClick = { settings = settings.copy(showClock = !settings.showClock) },
                                modifier = Modifier.fillMaxWidth().onFocusChanged { isClockFocused = it.isFocused },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                                    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                                    pressedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    pressedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Clock", style = MaterialTheme.typography.titleMedium)
                                        Text(if (settings.showClock) "Shown" else "Hidden", Modifier.alpha(0.68f))
                                    }
                                    ExpressiveToggle(checked = settings.showClock, onCheckedChange = null, isFocused = isClockFocused)
                                }
                            }
                        }
                    }
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small)) {
                        OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                        if (onPreview != null) OutlinedButton(onClick = { onPreview(settings) }) { Text("Full preview") }
                        Button(onClick = { onSave(settings) }) { Text("Save") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbientChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = ApertureTheme.spacing.medium),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
