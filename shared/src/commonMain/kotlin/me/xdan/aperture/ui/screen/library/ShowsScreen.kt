package me.xdan.aperture.ui.screen.library

import androidx.compose.ui.focus.focusProperties

import androidx.compose.ui.focus.focusRequester

import androidx.compose.ui.Alignment

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.ui.component.MediaCard
import me.xdan.aperture.ui.theme.ApertureTheme

@Composable
fun ShowsScreen(
    viewModel: LibraryViewModel,
    onMediaClick: (Long, FocusRequester, Boolean) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit,
    onActiveMediaChanged: (Long) -> Unit
) {
    val groups by viewModel.showGroups.collectAsState()
    val presentationMode by viewModel.showPresentationMode.collectAsState()
    if (groups.isEmpty()) {
        EmptyLibrary("No TV shows found.", drawerFocusRequester, contentEntryFocusRequester, onContentFocused)
        return
    }

    if (presentationMode == "episodes") {
        EpisodeRows(
            groups = groups,
            onMediaClick = onMediaClick,
            onMediaLongClick = onMediaLongClick,
            drawerFocusRequester = drawerFocusRequester,
            contentEntryFocusRequester = contentEntryFocusRequester,
            onContentFocused = onContentFocused,
            onActiveMediaChanged = onActiveMediaChanged
        )
    } else {
        val grid by viewModel.showGrid.collectAsState()
        GroupedShowsGrid(
            grid = grid,
            onSort = viewModel::sortShows,
            onUpdate = viewModel::updateShows,
            onMediaClick = onMediaClick,
            onMediaLongClick = onMediaLongClick,
            drawerFocusRequester = drawerFocusRequester,
            contentEntryFocusRequester = contentEntryFocusRequester,
            onContentFocused = onContentFocused,
            onActiveMediaChanged = onActiveMediaChanged
        )
    }
}

@Composable
private fun GroupedShowsGrid(
    grid: LocalGrid<ShowGroup>,
    onSort: (me.xdan.aperture.domain.catalog.LibraryQuery.Sort) -> Unit,
    onUpdate: ((me.xdan.aperture.domain.catalog.LibraryQuery) -> me.xdan.aperture.domain.catalog.LibraryQuery) -> Unit,
    onMediaClick: (Long, FocusRequester, Boolean) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit,
    onActiveMediaChanged: (Long) -> Unit
) {
    val groups = grid.items
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    // A new sort or filter starts from the top; the grid would otherwise stay anchored to a title.
    LaunchedEffect(grid.query) { gridState.scrollToItem(0) }
    val jump = me.xdan.aperture.ui.component.rememberGridJump(gridState)
    Row(Modifier.fillMaxSize().graphicsLayer { clip = false }) {
        Column(Modifier.weight(1f).padding(start = 32.dp, end = 16.dp, top = 24.dp).graphicsLayer { clip = false }) {
            Text("TV Shows", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(ApertureTheme.spacing.medium))
            me.xdan.aperture.ui.component.LibraryControlsRow(
                query = grid.query,
                options = grid.options,
                onSort = onSort,
                onUpdate = onUpdate,
                firstModifier = Modifier
                    .then(if (groups.isEmpty()) Modifier.focusRequester(contentEntryFocusRequester) else Modifier)
                    .then(if (drawerFocusRequester != null) Modifier.focusProperties { left = drawerFocusRequester } else Modifier)
            )
            Spacer(Modifier.height(ApertureTheme.spacing.small))
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nothing matches these filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }
            Box(Modifier.weight(1f).graphicsLayer { clip = false }) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxSize().graphicsLayer { clip = false },
                    contentPadding = PaddingValues(top = ApertureTheme.spacing.large, bottom = ApertureTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large),
                    horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large)
                ) {
                    itemsIndexed(groups, key = { _, group -> group.title }) { index, group ->
                        val media = group.representative
                        var isFocused by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.zIndex(if (isFocused) 1f else 0f)) {
                            AnimatedLibraryCard(media.id) {
                                MediaCard(
                                    media = media,
                                    onClick = { onMediaClick(media.id, it, false) },
                                    modifier = Modifier.fillMaxWidth(),
                                    focusRequester = jump.requesterFor(index, contentEntryFocusRequester),
                                    drawerFocusRequester = drawerFocusRequester.takeIf { index % 5 == 0 },
                                    onFocused = { requester ->
                                        isFocused = true
                                        onContentFocused(requester)
                                        onActiveMediaChanged(media.id)
                                    },
                                    onLongClick = { requester, opensToRight ->
                                        onMediaLongClick(media, requester, false, opensToRight, false)
                                    }
                                )
                            }
                        }
                        DisposableEffect(Unit) {
                            onDispose { isFocused = false }
                        }
                    }
                }
            }
        }
        if (grid.letters.size > 1 && groups.isNotEmpty()) {
            Box(Modifier.padding(top = 24.dp, end = 8.dp)) {
                me.xdan.aperture.ui.component.LetterRail(letters = grid.letters, onLetter = jump::jumpTo)
            }
        }
    }
}

@Composable
private fun EpisodeRows(
    groups: List<ShowGroup>,
    onMediaClick: (Long, FocusRequester, Boolean) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit,
    onActiveMediaChanged: (Long) -> Unit
) {
    val firstEpisode = groups.first().episodes.first()
    LazyColumn(
        modifier = Modifier.fillMaxSize().graphicsLayer { clip = false },
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item { Text("TV Shows", style = MaterialTheme.typography.headlineLarge) }
        groups.forEach { group ->
            item(key = "show:${group.title}") {
                Column(
                    modifier = Modifier.graphicsLayer { clip = false },
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(group.title, style = MaterialTheme.typography.headlineSmall)
                    group.seasons
                        .forEach { (season, seasonEpisodes) ->
                            Text(
                                if (season > 0) "Season $season" else "Episodes",
                                style = MaterialTheme.typography.titleMedium
                            )
                            LazyRow(
                                modifier = Modifier.graphicsLayer { clip = false },
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large)
                            ) {
                                itemsIndexed(seasonEpisodes, key = { _, episode -> episode.id }) { index, episode ->
                                    var isFocused by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.zIndex(if (isFocused) 1f else 0f).graphicsLayer { clip = false }) {
                                        AnimatedLibraryCard(episode.id) {
                                            Column(Modifier.width(220.dp).graphicsLayer { clip = false }) {
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
                                                    onFocused = { requester ->
                                                        isFocused = true
                                                        onContentFocused(requester)
                                                        onActiveMediaChanged(episode.id)
                                                    },
                                                    onLongClick = { requester, opensToRight ->
                                                        onMediaLongClick(episode, requester, false, opensToRight, true)
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
                                    DisposableEffect(Unit) {
                                        onDispose { isFocused = false }
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
