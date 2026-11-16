package me.xdan.aperture.ui.screen.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import me.xdan.aperture.domain.catalog.CatalogItem
import me.xdan.aperture.ui.platform.Dialog
import me.xdan.aperture.ui.theme.ApertureTheme

/**
 * A Jellyfin title's details. Playback and the file and stream information arrive in later steps;
 * for now this shows what the index knows.
 */
@Composable
fun JellyfinDetailsDialog(item: CatalogItem, onDismiss: () -> Unit) {
    val close = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val windowInfo = LocalWindowInfo.current
        LaunchedEffect(windowInfo.isWindowFocused) {
            if (!windowInfo.isWindowFocused) return@LaunchedEffect
            repeat(30) {
                withFrameNanos { }
                if (runCatching { close.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            }
        }
        Surface(
            modifier = Modifier.width(860.dp).heightIn(max = 560.dp),
            shape = ApertureTheme.shapes.dialog,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box {
                item.backdrop?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().clip(ApertureTheme.shapes.dialog)
                    )
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.horizontalGradient(
                                listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                            )
                        )
                    )
                }
                Column(Modifier.padding(ApertureTheme.spacing.huge).fillMaxWidth(0.66f)) {
                    Text(item.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    item.episodeLabel?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(ApertureTheme.spacing.small))
                    val facts = listOfNotNull(
                        item.year?.toString(),
                        item.runtimeMs?.takeIf { it > 0 }?.let { runtimeLabel(it) },
                        item.officialRating,
                        item.rating?.let { "★ %.1f".format(it) },
                        "Watched".takeIf { item.played }
                    )
                    if (facts.isNotEmpty()) {
                        Text(facts.joinToString("  ·  "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.genres.isNotEmpty()) {
                        Text(item.genres.joinToString(", "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(ApertureTheme.spacing.medium))
                    item.overview?.let {
                        Text(it, maxLines = 7, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(ApertureTheme.spacing.large))
                    Text(
                        "Playing Jellyfin titles arrives with the player update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(ApertureTheme.spacing.medium))
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.focusRequester(close)) { Text("Close") }
                }
            }
        }
    }
}

private fun runtimeLabel(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
}
