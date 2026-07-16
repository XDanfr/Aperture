@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import me.xdan.aperture.domain.model.AmbientBrandPlacement
import me.xdan.aperture.domain.model.AmbientModeType
import me.xdan.aperture.domain.model.AmbientSettings

@Composable
internal fun AmbientSettingsDialog(
    initial: AmbientSettings,
    onSave: (AmbientSettings) -> Unit,
    onPreview: (AmbientSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var settings by remember(initial) { mutableStateOf(initial) }
    val firstRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstRequester.requestFocus() }
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
                modifier = Modifier.width(700.dp),
                shape = RoundedCornerShape(24.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(36.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text("Ambient mode", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Choose what Aperture shows after five minutes without input.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )

                    Text("Style", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AmbientChoice(
                            label = "Cinematic",
                            selected = settings.mode == AmbientModeType.CINEMATIC,
                            onClick = { settings = settings.copy(mode = AmbientModeType.CINEMATIC) },
                            modifier = Modifier.weight(1f).focusRequester(firstRequester)
                        )
                        AmbientChoice(
                            label = "Poster wall",
                            selected = settings.mode == AmbientModeType.POSTER_WALL,
                            onClick = { settings = settings.copy(mode = AmbientModeType.POSTER_WALL) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (settings.mode == AmbientModeType.POSTER_WALL) {
                        Text("Brand placement", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AmbientChoice(
                                label = "Top left logo",
                                selected = settings.wallBrandPlacement == AmbientBrandPlacement.TOP_LEFT,
                                onClick = {
                                    settings = settings.copy(
                                        wallBrandPlacement = AmbientBrandPlacement.TOP_LEFT
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            AmbientChoice(
                                label = "Centre icon",
                                selected = settings.wallBrandPlacement == AmbientBrandPlacement.CENTRE,
                                onClick = {
                                    settings = settings.copy(
                                        wallBrandPlacement = AmbientBrandPlacement.CENTRE
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Surface(
                        onClick = { settings = settings.copy(showClock = !settings.showClock) },
                        modifier = Modifier.fillMaxWidth(),
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
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Clock", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (settings.showClock) "Shown with Aperture branding" else "Hidden",
                                    modifier = Modifier.alpha(0.68f)
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = settings.showClock,
                                onCheckedChange = null,
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                        OutlinedButton(onClick = { onPreview(settings) }) { Text("Preview") }
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
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp)
        )
    }
}
