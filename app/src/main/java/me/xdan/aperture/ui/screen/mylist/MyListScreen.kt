package me.xdan.aperture.ui.screen.mylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.ui.component.MediaCard
import me.xdan.aperture.data.local.entity.MediaEntity

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MyListScreen(
    viewModel: MyListViewModel,
    onMediaClick: (Long, FocusRequester) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        val columnCount = ((maxWidth - 16.dp) / 182.dp).toInt().coerceAtLeast(1)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(media, key = { _, item -> item.id }) { index, item ->
                var visible by remember(item.id) { mutableStateOf(false) }
                LaunchedEffect(item.id) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.94f)
                ) {
                    MediaCard(
                        media = item,
                        onClick = { requester -> onMediaClick(item.id, requester) },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = contentEntryFocusRequester.takeIf { item.id == media.first().id },
                        drawerFocusRequester = drawerFocusRequester.takeIf { index % columnCount == 0 },
                        onFocused = onContentFocused,
                        onLongClick = { requester, opensToRight -> onMediaLongClick(item, requester, false, opensToRight) }
                    )
                }
            }
        }
    }
}
