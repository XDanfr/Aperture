package me.xdan.aperture.ui.screen.mylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.ui.component.MediaCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MyListScreen(
    viewModel: MyListViewModel,
    onMediaClick: (Long, FocusRequester) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit
) {
    val media by viewModel.media.collectAsState()

    if (media.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (drawerFocusRequester != null) {
                        Modifier.focusProperties { left = drawerFocusRequester }
                    } else Modifier
                )
                .focusRequester(contentEntryFocusRequester)
                .focusable()
                .onFocusChanged {
                    if (it.isFocused) onContentFocused(contentEntryFocusRequester)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Your List is empty. Add titles from their details popup.",
                style = MaterialTheme.typography.titleLarge
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(media, key = { it.id }) { item ->
            MediaCard(
                media = item,
                onClick = { requester -> onMediaClick(item.id, requester) },
                modifier = Modifier.fillMaxWidth(),
                focusRequester = contentEntryFocusRequester.takeIf { item.id == media.first().id },
                drawerFocusRequester = drawerFocusRequester,
                onFocused = onContentFocused
            )
        }
    }
}
