package me.xdan.aperture.ui.screen.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import me.xdan.aperture.ui.component.MediaCard
import me.xdan.aperture.ui.theme.ApertureTheme
import me.xdan.aperture.data.local.entity.MediaEntity

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onMediaClick: (Long, FocusRequester) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .graphicsLayer { clip = false }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            colors = SurfaceDefaults.colors(containerColor = Color.DarkGray)
        ) {
            BasicTextField(
                value = query,
                onValueChange = { viewModel.onQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (drawerFocusRequester != null) {
                            Modifier.focusProperties { left = drawerFocusRequester }
                        } else Modifier
                    )
                    .focusRequester(contentEntryFocusRequester)
                    .onFocusChanged {
                        if (it.isFocused) onContentFocused(contentEntryFocusRequester)
                    }
                    .padding(16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text("Search Movies and TV Shows...", color = Color.Gray)
                    }
                    innerTextField()
                }
            )
        }

        Spacer(modifier = Modifier.height(ApertureTheme.spacing.large))

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(top = ApertureTheme.spacing.large, bottom = ApertureTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium),
            modifier = Modifier.fillMaxSize().graphicsLayer { clip = false }
        ) {
            itemsIndexed(results, key = { _, media -> media.id }) { index, media ->
                var isFocused by remember { mutableStateOf(false) }
                Box(modifier = Modifier.zIndex(if (isFocused) 1f else 0f)) {
                    MediaCard(
                        media = media,
                        onClick = { requester -> onMediaClick(media.id, requester) },
                        modifier = Modifier.fillMaxWidth(),
                        drawerFocusRequester = drawerFocusRequester.takeIf { index % 5 == 0 },
                        onFocused = {
                            isFocused = true
                            onContentFocused(it)
                        },
                        onLongClick = { requester, opensToRight -> onMediaLongClick(media, requester, false, opensToRight) }
                    )
                }
                DisposableEffect(Unit) {
                    onDispose { isFocused = false }
                }
            }
        }
    }
}
