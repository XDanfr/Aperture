package me.xdan.aperture.ui.screen.library

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.ui.component.MediaCard
import me.xdan.aperture.ui.component.LetterRail
import me.xdan.aperture.ui.component.LibraryControlsRow
import me.xdan.aperture.ui.component.rememberGridJump
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import me.xdan.aperture.ui.theme.ApertureTheme
import me.xdan.aperture.data.local.entity.MediaEntity

@Composable
fun MoviesScreen(
    viewModel: LibraryViewModel,
    onMediaClick: (Long, FocusRequester) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit,
    onActiveMediaChanged: (Long) -> Unit
) {
    val grid by viewModel.movieGrid.collectAsState()
    val movies = grid.items
    if (grid.total == 0) {
        EmptyLibrary("No movies found.", drawerFocusRequester, contentEntryFocusRequester, onContentFocused)
        return
    }
    val gridState = rememberLazyGridState()
    // A new sort or filter starts from the top; the grid would otherwise stay anchored to a title.
    LaunchedEffect(grid.query) { gridState.scrollToItem(0) }
    val jump = rememberGridJump(gridState)
    val controlsRequester = remember { FocusRequester() }

    Row(Modifier.fillMaxSize().graphicsLayer { clip = false }) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 32.dp, end = 16.dp, top = 24.dp)
                .graphicsLayer { clip = false }
        ) {
            Text("Movies", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(ApertureTheme.spacing.medium))
            LibraryControlsRow(
                query = grid.query,
                options = grid.options,
                onSort = viewModel::sortMovies,
                onUpdate = viewModel::updateMovies,
                // With no results, the controls are where the sidebar's Right lands.
                firstModifier = Modifier
                    .then(if (movies.isEmpty()) Modifier.focusRequester(contentEntryFocusRequester) else Modifier.focusRequester(controlsRequester))
                    .then(if (drawerFocusRequester != null) Modifier.focusProperties { left = drawerFocusRequester } else Modifier)
            )
            Spacer(Modifier.height(ApertureTheme.spacing.small))
            if (movies.isEmpty()) {
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
                    itemsIndexed(movies, key = { _, item -> item.id }) { index, item ->
                        var isFocused by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.zIndex(if (isFocused) 1f else 0f)) {
                            AnimatedMovieCard(
                                media = item,
                                focusRequester = jump.requesterFor(index, contentEntryFocusRequester),
                                isLeftmost = index % 5 == 0,
                                onMediaClick = onMediaClick,
                                onMediaLongClick = onMediaLongClick,
                                drawerFocusRequester = drawerFocusRequester,
                                onContentFocused = {
                                    isFocused = true
                                    onContentFocused(it)
                                },
                                onActiveMediaChanged = onActiveMediaChanged
                            )
                        }
                        DisposableEffect(Unit) {
                            onDispose { isFocused = false }
                        }
                    }
                }
            }
        }
        if (grid.letters.size > 1 && movies.isNotEmpty()) {
            Box(Modifier.padding(top = 24.dp, end = 8.dp)) {
                LetterRail(letters = grid.letters, onLetter = jump::jumpTo)
            }
        }
    }
}

@Composable
private fun AnimatedMovieCard(
    media: MediaEntity,
    focusRequester: FocusRequester,
    isLeftmost: Boolean,
    onMediaClick: (Long, FocusRequester) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    onContentFocused: (FocusRequester) -> Unit,
    onActiveMediaChanged: (Long) -> Unit
) {
    var visible by remember(media.id) { mutableStateOf(false) }
    LaunchedEffect(media.id) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.94f)
    ) {
        MediaCard(
            media = media,
            onClick = { onMediaClick(media.id, it) },
            modifier = Modifier.fillMaxWidth(),
            focusRequester = focusRequester,
            drawerFocusRequester = drawerFocusRequester.takeIf { isLeftmost },
            onFocused = { requester ->
                onContentFocused(requester)
                onActiveMediaChanged(media.id)
            },
            onLongClick = { requester, opensToRight ->
                onMediaLongClick(media, requester, false, opensToRight)
            }
        )
    }
}

@Composable
internal fun EmptyLibrary(
    message: String,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit
) {
    Box(
        Modifier.fillMaxSize()
            .then(if (drawerFocusRequester != null) Modifier.focusProperties { left = drawerFocusRequester } else Modifier)
            .focusRequester(contentEntryFocusRequester)
            .focusable()
            .onFocusChanged { if (it.isFocused) onContentFocused(contentEntryFocusRequester) },
        contentAlignment = Alignment.Center
    ) {
        Text(message, style = MaterialTheme.typography.titleLarge)
    }
}
