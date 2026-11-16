package me.xdan.aperture.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import me.xdan.aperture.domain.catalog.MediaInfo
import me.xdan.aperture.ui.component.expressive.ExpressiveLoadingIndicator
import me.xdan.aperture.ui.platform.Dialog
import me.xdan.aperture.ui.theme.ApertureTheme

/**
 * A title's file and stream details. [info] is null while loading; [error] shows instead when
 * the details couldn't be read. Rows take focus so a remote can scroll through long track lists.
 */
@Composable
fun MediaInfoDialog(title: String, info: MediaInfo?, error: String? = null, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val close = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val windowInfo = LocalWindowInfo.current
        LaunchedEffect(windowInfo.isWindowFocused, info != null, error) {
            if (!windowInfo.isWindowFocused) return@LaunchedEffect
            repeat(30) {
                withFrameNanos { }
                val target = if (info != null && info.sections.isNotEmpty()) first else close
                if (runCatching { target.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            }
        }
        Surface(
            modifier = Modifier.width(720.dp).heightIn(max = 640.dp),
            shape = ApertureTheme.shapes.dialog,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(ApertureTheme.spacing.extraLarge)) {
                Text("Info", style = MaterialTheme.typography.headlineSmall)
                Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(ApertureTheme.spacing.medium))
                when {
                    error != null -> Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    info == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        ExpressiveLoadingIndicator(Modifier.size(40.dp))
                    }
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        info.sections.forEachIndexed { sectionIndex, section ->
                            item(key = "h$sectionIndex") {
                                Text(
                                    section.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = if (sectionIndex == 0) 0.dp else 14.dp, bottom = 4.dp, start = 12.dp)
                                )
                            }
                            itemsIndexed(section.rows, key = { index, _ -> "r$sectionIndex.$index" }) { index, row ->
                                InfoRow(row, if (sectionIndex == 0 && index == 0) Modifier.focusRequester(first) else Modifier)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(ApertureTheme.spacing.medium))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).focusRequester(close)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun InfoRow(row: MediaInfo.Row, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .focusable(interactionSource = interaction)
            .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (row.label != null) {
            Text(row.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        }
        Text(row.value, modifier = Modifier.weight(1f))
    }
}
