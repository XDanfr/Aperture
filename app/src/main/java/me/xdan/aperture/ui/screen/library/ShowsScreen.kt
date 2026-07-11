package me.xdan.aperture.ui.screen.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.ui.component.MediaCard
import me.xdan.aperture.data.local.entity.MediaEntity

@Composable
fun ShowsScreen(
    viewModel: LibraryViewModel,
    onMediaClick: (Long, FocusRequester) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit
) {
    val episodes by viewModel.shows.collectAsState()
    if (episodes.isEmpty()) {
        EmptyLibrary("No TV shows found.", drawerFocusRequester, contentEntryFocusRequester, onContentFocused)
        return
    }
    val shows = episodes.groupBy { it.title }
    val firstEpisode = episodes.first()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item { Text("TV Shows", style = MaterialTheme.typography.headlineLarge) }
        shows.forEach { (title, showEpisodes) ->
            item(key = "show:$title") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    showEpisodes.groupBy { it.seasonNumber ?: 0 }.toSortedMap().forEach { (season, seasonEpisodes) ->
                        Text(
                            if (season > 0) "Season $season" else "Episodes",
                            style = MaterialTheme.typography.titleMedium
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(seasonEpisodes, key = { it.id }) { episode ->
                                Column(Modifier.width(154.dp)) {
                                    MediaCard(
                                        media = episode,
                                        onClick = { onMediaClick(episode.id, it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        focusRequester = contentEntryFocusRequester.takeIf { episode.id == firstEpisode.id },
                                        drawerFocusRequester = drawerFocusRequester,
                                        onFocused = onContentFocused,
                                        onLongClick = { requester, opensToRight -> onMediaLongClick(episode, requester, false, opensToRight) }
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        episode.episodeNumber?.let { "Episode $it" } ?: episode.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
