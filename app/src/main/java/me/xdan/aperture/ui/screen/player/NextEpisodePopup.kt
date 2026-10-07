package me.xdan.aperture.ui.screen.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.ui.component.ArtworkFallback
import me.xdan.aperture.ui.theme.ApertureTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NextEpisodePopup(
    episode: MediaEntity,
    secondsRemaining: Int,
    focusRequester: FocusRequester,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissRequester = remember { FocusRequester() }
    val title = episode.episodeTitle ?: "Episode ${episode.episodeNumber ?: "?"}"
    Column(Modifier.width(360.dp), horizontalAlignment = Alignment.End) {
        Surface(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth()
                .focusRequester(focusRequester)
                .focusProperties {
                    down = dismissRequester
                    up = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                },
            shape = ClickableSurfaceDefaults.shape(ApertureTheme.shapes.poster),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape = ApertureTheme.shapes.poster),
                focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = ApertureTheme.shapes.poster)
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Up next", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(132.dp).height(74.dp).clip(ApertureTheme.shapes.poster)) {
                        if (!episode.stillPath.isNullOrBlank()) {
                            AsyncImage(
                                model = TmdbApi.IMAGE_BASE_URL + "w300" + episode.stillPath,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            ArtworkFallback(title = title, isFocused = false)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Season ${episode.seasonNumber ?: "?"} · Episode ${episode.episodeNumber ?: "?"}", style = MaterialTheme.typography.labelMedium)
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text("Playing in $secondsRemaining…", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.focusRequester(dismissRequester).focusProperties {
                up = focusRequester
                down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Dismiss")
        }
    }
}
