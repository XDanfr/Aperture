package me.xdan.aperture.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import me.xdan.aperture.ui.component.expressive.ExpressiveSlider
import me.xdan.aperture.ui.theme.ApertureTheme

@Composable
fun SubtitleAppearanceDialog(
    initial: SubtitleAppearanceSettings,
    onSave: (SubtitleAppearanceSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var settings by remember(initial) { mutableStateOf(initial) }
    val firstRequester = remember { FocusRequester() }
    val colours = listOf("white", "yellow", "cyan")
    val previewTextColour = subtitlePreviewColour(settings.colour)

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(840.dp)
                .heightIn(max = 820.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                Modifier.padding(ApertureTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)
            ) {
                Text("Subtitle Appearance", style = MaterialTheme.typography.headlineSmall)

                SubtitleAppearanceSlider(
                    title = "Text size",
                    value = settings.textScale,
                    onValueChange = { settings = settings.copy(textScale = it) },
                    valueRange = 0.7f..1.6f,
                    valueText = "${(settings.textScale * 100).toInt()}%",
                    focusRequester = firstRequester
                )

                SubtitleAppearanceSlider(
                    title = "Background opacity",
                    value = settings.backgroundOpacity,
                    onValueChange = { settings = settings.copy(backgroundOpacity = it) },
                    valueRange = 0f..0.9f,
                    valueText = "${(settings.backgroundOpacity * 100).toInt()}%"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Text colour",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        colours.forEach { colourName ->
                            val selected = settings.colour == colourName
                            Surface(
                                onClick = { settings = settings.copy(colour = colourName) },
                                modifier = Modifier.size(56.dp),
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                scale = ClickableSurfaceDefaults.scale(
                                    focusedScale = 1.1f,
                                    pressedScale = 0.94f
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(
                                        border = androidx.compose.foundation.BorderStroke(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary
                                        ),
                                        shape = CircleShape
                                    )
                                )
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Box(
                                        Modifier
                                            .size(32.dp)
                                            .background(subtitlePreviewColour(colourName), CircleShape)
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = SurfaceDefaults.colors(containerColor = Color(0xFF17171B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ApertureTheme.spacing.small),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This is a subtitle.",
                            modifier = Modifier
                                .background(
                                    Color(0xFF0C0C0E).copy(
                                        alpha = settings.backgroundOpacity.coerceIn(0f, 0.9f)
                                    ),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            color = previewTextColour,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = (36f * settings.textScale).sp,
                                lineHeight = (44f * settings.textScale).sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = ApertureTheme.spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(settings) }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun SubtitleAppearanceSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExpressiveSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                isFocused = isFocused,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused }
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            )
            Text(
                valueText,
                modifier = Modifier.width(64.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun subtitlePreviewColour(colour: String): Color = when (colour) {
    "yellow" -> Color.Yellow
    "cyan" -> Color.Cyan
    else -> Color.White
}
