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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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

    // Laid out like the local Movies and Shows grids, so the two feel the same.
    Row(Modifier.fillMaxSize().graphicsLayer { clip = false }) {
        Column(
            Modifier
                .weight(1f)
                .padding(start = 32.dp, end = 16.dp, top = 24.dp)
                .graphicsLayer { clip = false }
        ) {
            // Header: name and count, then sort and filters.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(state.name, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.width(ApertureTheme.spacing.medium))
                if (items != null) {
                    Text(
                        "%,d %s".format(items.size, if (items.size == 1) "title" else "titles"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(ApertureTheme.spacing.medium))
            LibraryControlsRow(
                query = state.query,
                options = state.options,
                onSort = viewModel::sortBy,
                onUpdate = viewModel::update,
                firstModifier = (if (drawerFocusRequester != null) Modifier.focusProperties { left = drawerFocusRequester } else Modifier)
                    // With no cards, the sort button is where the page starts.
                    .then(if (items.isNullOrEmpty()) Modifier.focusRequester(contentEntryFocusRequester) else Modifier)
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
                else -> {
                    // Only the first column hands Left to the sidebar; the rest move along the row.
                    val columns = GRID_COLUMNS
                    LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    contentPadding = PaddingValues(top = ApertureTheme.spacing.large, bottom = ApertureTheme.spacing.large),
                    horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large),
                    // The grid is the entry point, so focus lands on a card that's on screen (the
                    // last one used, or the first visible) even when the grid is scrolled down.
                    modifier = Modifier.fillMaxSize().graphicsLayer { clip = false }
                        .focusRequester(contentEntryFocusRequester)
                        .focusRestorer()
                ) {
                    itemsIndexed(items, key = { _, item -> item.key.stableId }) { index, item ->
                        val requester = cardRequesters.getOrPut(index) { FocusRequester() }
                        var isFocused by remember { mutableStateOf(false) }
                        // Cards fade and grow in as they arrive, as on the local grids.
                        var visible by remember(item.key) { mutableStateOf(false) }
                        LaunchedEffect(item.key) { visible = true }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = visible,
                            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)) +
                                androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(260), initialScale = 0.94f),
                            modifier = Modifier.zIndex(if (isFocused) 1f else 0f)
                        ) {
                            CatalogCard(
                                item = item,
                                onClick = { onItemClick(item, it) },
                                modifier = Modifier.fillMaxWidth(),
                                focusRequester = requester,
                                drawerFocusRequester = drawerFocusRequester.takeIf { index % columns == 0 },
                                onFocused = {
                                    isFocused = true
                                    onContentFocused(it)
                                }
                            )
                        }
                    }
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

/** The same five columns as the local Movies and Shows grids. */
private const val GRID_COLUMNS = 5
