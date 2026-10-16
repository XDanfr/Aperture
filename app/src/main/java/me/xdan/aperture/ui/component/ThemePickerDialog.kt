package me.xdan.aperture.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import me.xdan.aperture.ui.theme.ApertureTheme
import me.xdan.aperture.ui.theme.ApertureThemeOptions

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ThemePickerDialog(
    selectedThemeId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onboarding: Boolean = false
) {
    val firstRequester = remember { FocusRequester() }
    val themeRows = remember { ApertureThemeOptions.chunked(3) }
    val initialThemeId = remember { selectedThemeId }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = ApertureThemeOptions.indexOfFirst { it.id == initialThemeId }.coerceAtLeast(0) / 3
    )

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstRequester.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(820.dp).heightIn(max = 720.dp),
                shape = ApertureTheme.shapes.dialog,
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(ApertureTheme.spacing.huge)) {
                    Text("Choose a theme", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Choose a palette for Aperture.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                    Spacer(Modifier.height(ApertureTheme.spacing.large))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            top = 12.dp,
                            bottom = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)
                    ) {
                        items(
                            themeRows,
                            key = { row -> row.firstOrNull()?.id ?: "empty" }
                        ) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    ApertureTheme.spacing.medium
                                )
                            ) {
                                repeat(3) { index ->
                                    val option = row.getOrNull(index)

                                    if (option != null) {
                                        ThemeOptionCard(
                                            option = option,
                                            selected = option.id == selectedThemeId,
                                            firstRequester = if (
                                                option.id == initialThemeId
                                            ) firstRequester else null,
                                            onSelect = onSelect,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(152.dp)
                                        )
                                    } else {
                                        Spacer(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(152.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(ApertureTheme.spacing.medium))
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(if (onboarding) "Continue" else "Done") }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ThemeOptionCard(
    option: me.xdan.aperture.ui.theme.ApertureThemeOption,
    selected: Boolean,
    firstRequester: FocusRequester?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onSelect(option.id) },
        modifier = modifier
            .then(
                if (firstRequester != null) {
                    Modifier.focusRequester(firstRequester)
                } else {
                    Modifier
                }
            ),
        shape = ClickableSurfaceDefaults.shape(ApertureTheme.shapes.poster),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1.025f,
            pressedScale = 0.98f
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary
                ),
                shape = ApertureTheme.shapes.poster
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ThemeColourSwatch(
                option = option,
                selected = selected
            )

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.label,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }


        }
    }
}

@Composable
private fun ThemeColourSwatch(
    option: me.xdan.aperture.ui.theme.ApertureThemeOption,
    selected: Boolean
) {
    val outlineColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }

    Canvas(
        modifier = Modifier.size(88.dp)
    ) {
        val strokeWidth = if (selected) 3.dp.toPx() else 1.dp.toPx()

        if (option.id == "dynamic") {
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                )
            )
        } else {
            drawArc(
                color = option.primary,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true
            )
            drawArc(
                color = option.secondary,
                startAngle = 90f,
                sweepAngle = 90f,
                useCenter = true
            )
            drawArc(
                color = option.tertiary,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = true
            )
        }
        drawCircle(
            color = outlineColor,
            radius = size.minDimension / 2f - strokeWidth / 2f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
    }
}
