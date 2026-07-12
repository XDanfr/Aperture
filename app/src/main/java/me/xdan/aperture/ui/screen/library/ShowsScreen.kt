package me.xdan.aperture.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.ui.component.MediaCard

@Composable
fun ShowsScreen(
    viewModel: LibraryViewModel,
    onMediaClick: (Long, FocusRequester, Boolean) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit
) {
    val episodes by viewModel.shows.collectAsState()
    val presentationMode by viewModel.showPresentationMode.collectAsState()
    if (episodes.isEmpty()) {
        EmptyLibrary("No TV shows found.", drawerFocusRequester, contentEntryFocusRequester, onContentFocused)
        return
    }

    val groups = remember(episodes) {
        episodes.groupBy { it.title }
            .map { (title, items) -> ShowGroup(title, items) }
            .sortedBy { it.title.lowercase() }
    }
    if (presentationMode == "episodes") {
        EpisodeRows(
            groups = groups,
            onMediaClick = onMediaClick,
            onMediaLongClick = onMediaLongClick,
            drawerFocusRequester = drawerFocusRequester,
            contentEntryFocusRequester = contentEntryFocusRequester,
            onContentFocused = onContentFocused
        )
    } else {
        GroupedShowsGrid(
            groups = groups,
            onMediaClick = onMediaClick,
            onMediaLongClick = onMediaLongClick,
            drawerFocusRequester = drawerFocusRequester,
            contentEntryFocusRequester = contentEntryFocusRequester,
            onContentFocused = onContentFocused
        )
    }
}

@Composable
private fun GroupedShowsGrid(
    groups: List<ShowGroup>,
    onMediaClick: (Long, FocusRequester, Boolean) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Text("TV Shows", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(20.dp))
        BoxWithConstraints(Modifier.weight(1f)) {
            val columnCount = ((maxWidth - 16.dp) / 182.dp).toInt().coerceAtLeast(1)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(groups, key = { _, group -> group.title }) { index, group ->
                    val media = group.representative
                    AnimatedLibraryCard(media.id) {
                        MediaCard(
                            media = media,
                            onClick = { onMediaClick(media.id, it, false) },
                            modifier = Modifier.fillMaxWidth(),
                            focusRequester = contentEntryFocusRequester.takeIf { index == 0 },
                            drawerFocusRequester = drawerFocusRequester.takeIf { index % columnCount == 0 },
                            onFocused = onContentFocused,
                            onLongClick = { requester, opensToRight ->
                                onMediaLongClick(media, requester, false, opensToRight)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRows(
    groups: List<ShowGroup>,
    onMediaClick: (Long, FocusRequester, Boolean) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit
) {
    val firstEpisode = groups.first().episodes.first()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item { Text("TV Shows", style = MaterialTheme.typography.headlineLarge) }
        groups.forEach { group ->
            item(key = "show:${group.title}") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(group.title, style = MaterialTheme.typography.headlineSmall)
                    group.episodes.groupBy { it.seasonNumber ?: 0 }.toSortedMap()
                        .forEach { (season, seasonEpisodes) ->
                            Text(
                                if (season > 0) "Season $season" else "Episodes",
                                style = MaterialTheme.typography.titleMedium
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(seasonEpisodes, key = { _, episode -> episode.id }) { index, episode ->
                                    AnimatedLibraryCard(episode.id) {
                                        Column(Modifier.width(220.dp)) {
                                            MediaCard(
                                                media = episode,
                                                onClick = { onMediaClick(episode.id, it, true) },
                                                modifier = Modifier.fillMaxWidth(),
                                                aspectRatio = 16f / 9f,
                                                preferEpisodeStill = true,
                                                focusRequester = contentEntryFocusRequester.takeIf {
                                                    episode.id == firstEpisode.id
                                                },
                                                drawerFocusRequester = drawerFocusRequester.takeIf { index == 0 },
                                                onFocused = onContentFocused,
                                                onLongClick = { requester, opensToRight ->
                                                    onMediaLongClick(episode, requester, false, opensToRight)
                                                }
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                episodeLabel(episode),
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
}

@Composable
private fun AnimatedLibraryCard(key: Long, content: @Composable () -> Unit) {
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.94f)
    ) { content() }
}

private fun episodeLabel(media: MediaEntity): String {
    val number = buildString {
        media.seasonNumber?.let { append("S$it") }
        media.episodeNumber?.let { append("E$it") }
    }
    return listOf(number.takeIf { it.isNotBlank() }, media.episodeTitle)
        .filterNotNull()
        .joinToString(" · ")
        .ifBlank { media.title }
}
