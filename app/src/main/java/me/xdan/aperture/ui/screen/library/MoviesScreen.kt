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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.ui.component.MediaCard
import me.xdan.aperture.data.local.entity.MediaEntity

@Composable
fun MoviesScreen(
    viewModel: LibraryViewModel,
    onMediaClick: (Long, FocusRequester) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit
) {
    val movies by viewModel.movies.collectAsState()
    if (movies.isEmpty()) {
        EmptyLibrary("No movies found.", drawerFocusRequester, contentEntryFocusRequester, onContentFocused)
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Text("Movies", style = MaterialTheme.typography.headlineLarge)
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
                itemsIndexed(movies, key = { _, item -> item.id }) { index, item ->
                    AnimatedMovieCard(
                        media = item,
                        isFirst = item.id == movies.first().id,
                        isLeftmost = index % columnCount == 0,
                        onMediaClick = onMediaClick,
                        onMediaLongClick = onMediaLongClick,
                        drawerFocusRequester = drawerFocusRequester,
                        contentEntryFocusRequester = contentEntryFocusRequester,
                        onContentFocused = onContentFocused
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedMovieCard(
    media: MediaEntity,
    isFirst: Boolean,
    isLeftmost: Boolean,
    onMediaClick: (Long, FocusRequester) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit
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
            focusRequester = contentEntryFocusRequester.takeIf { isFirst },
            drawerFocusRequester = drawerFocusRequester.takeIf { isLeftmost },
            onFocused = onContentFocused,
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
