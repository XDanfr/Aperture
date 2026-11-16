package me.xdan.aperture.ui.screen.jellyfin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import kotlinx.coroutines.launch
import me.xdan.aperture.domain.catalog.CatalogItem
import me.xdan.aperture.domain.catalog.LibraryQuery
import me.xdan.aperture.ui.component.CatalogCard
import me.xdan.aperture.ui.component.LetterRail
import me.xdan.aperture.ui.component.LibraryControlsRow
import me.xdan.aperture.ui.component.expressive.ExpressiveLoadingIndicator
import me.xdan.aperture.ui.platform.Dialog
import me.xdan.aperture.ui.theme.ApertureTheme

/** A Jellyfin library as a poster grid with sort, filters and an A–Z rail. */
@Composable
fun JellyfinLibraryScreen(
    viewModel: JellyfinLibraryViewModel,
    onItemClick: (CatalogItem, FocusRequester) -> Unit,
    drawerFocusRequester: FocusRequester? = null,
    contentEntryFocusRequester: FocusRequester = FocusRequester.Default,
    onContentFocused: (FocusRequester) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val items = state.items
    val gridState = rememberLazyGridState()
    // A new sort or filter starts from the top; the grid would otherwise stay anchored to a title.
    LaunchedEffect(state.query) { gridState.scrollToItem(0) }
    val scope = rememberCoroutineScope()
    val cardRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    // Where focus goes after a letter jump, once the target card has been composed.
    var pendingFocusIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(pendingFocusIndex) {
        val index = pendingFocusIndex ?: return@LaunchedEffect
        repeat(20) {
            withFrameNanos { }
            if (runCatching { cardRequesters[index]?.requestFocus() }.getOrNull() == true) {
                pendingFocusIndex = null
                return@LaunchedEffect
            }
        }
        pendingFocusIndex = null
    }

    Row(Modifier.fillMaxSize().padding(start = 40.dp, top = 32.dp, end = 16.dp)) {
        Column(Modifier.weight(1f)) {
            // Header: name and count, then sort and filters.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(state.name, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.width(ApertureTheme.spacing.medium))
                if (items != null) {
                    Text(
                        "%,d %s".format(items.size, if (items.size == 1) "title" else "titles"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(ApertureTheme.spacing.medium))
            LibraryControlsRow(
                query = state.query,
                options = state.options,
                onSort = viewModel::sortBy,
                onUpdate = viewModel::update,
                firstModifier = if (drawerFocusRequester != null) Modifier.focusProperties { left = drawerFocusRequester } else Modifier
            )
            Spacer(Modifier.height(ApertureTheme.spacing.medium))

            when {
                items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator(modifier = Modifier.size(48.dp))
                }
                items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.query.isFiltered) "Nothing matches these filters." else "This library is empty, or still syncing.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp, end = 12.dp, start = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(items, key = { _, item -> item.key.stableId }) { index, item ->
                        val requester = if (index == 0) contentEntryFocusRequester else cardRequesters.getOrPut(index) { FocusRequester() }
                        if (index == 0) cardRequesters[0] = contentEntryFocusRequester
                        CatalogCard(
                            item = item,
                            onClick = { onItemClick(item, it) },
                            focusRequester = requester,
                            drawerFocusRequester = drawerFocusRequester,
                            onFocused = onContentFocused
                        )
                    }
                }
            }
        }

        // A–Z: only when sorted by title, and only letters that have titles.
        if (state.letters.size > 1 && !items.isNullOrEmpty()) {
            LetterRail(
                letters = state.letters,
                onLetter = { index ->
                    scope.launch {
                        gridState.scrollToItem(index)
                        pendingFocusIndex = index
                    }
                }
            )
        }
    }

}
