package me.xdan.aperture.ui.screen.mylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.ui.component.MediaCard
import me.xdan.aperture.ui.theme.ApertureTheme
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

    // Focus Pull
    LaunchedEffect(media.isNotEmpty()) {
        if (media.isNotEmpty()) {
            contentEntryFocusRequester.requestFocus()
        }
    }

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

    Box(modifier = Modifier.fillMaxSize().graphicsLayer { clip = false }) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxSize().graphicsLayer { clip = false },
            contentPadding = PaddingValues(top = ApertureTheme.spacing.huge, bottom = ApertureTheme.spacing.large, start = 32.dp, end = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large)
        ) {
            itemsIndexed(media, key = { _, item -> item.id }) { index, item ->
                var visible by remember(item.id) { mutableStateOf(false) }
                var isFocused by remember { mutableStateOf(false) }
                LaunchedEffect(item.id) { visible = true }
                Box(modifier = Modifier.zIndex(if (isFocused) 1f else 0f)) {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.94f)
                    ) {
                        MediaCard(
                            media = item,
                            onClick = { requester -> onMediaClick(item.id, requester) },
                            modifier = Modifier.fillMaxWidth(),
                            focusRequester = contentEntryFocusRequester.takeIf { item.id == media.first().id },
                            drawerFocusRequester = drawerFocusRequester.takeIf { index % 6 == 0 },
                            onFocused = {
                                isFocused = true
                                onContentFocused(it)
                            },
                            onLongClick = { requester, opensToRight -> onMediaLongClick(item, requester, false, opensToRight) }
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
