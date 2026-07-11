package me.xdan.aperture.ui.screen.library

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies, key = { it.id }) { item ->
                MediaCard(
                    media = item,
                    onClick = { onMediaClick(item.id, it) },
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = contentEntryFocusRequester.takeIf { item.id == movies.first().id },
                    drawerFocusRequester = drawerFocusRequester,
                    onFocused = onContentFocused,
                    onLongClick = { requester, opensToRight -> onMediaLongClick(item, requester, false, opensToRight) }
                )
            }
        }
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
