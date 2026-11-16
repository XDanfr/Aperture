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
    val scope = rememberCoroutineScope()
    val cardRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    var showSort by remember { mutableStateOf(false) }
    var showGenres by remember { mutableStateOf(false) }
    val sortRequester = remember { FocusRequester() }
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
            Row(horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small)) {
                val q = state.query
                OutlinedButton(
                    onClick = { showSort = true },
                    modifier = Modifier
                        .focusRequester(sortRequester)
                        .then(if (drawerFocusRequester != null) Modifier.focusProperties { left = drawerFocusRequester } else Modifier)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(q.sort.label)
                    if (q.sort != LibraryQuery.Sort.RANDOM) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (q.descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                            contentDescription = if (q.descending) "Descending" else "Ascending",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                FilterToggle("Unwatched", q.unwatchedOnly) { viewModel.update { it.copy(unwatchedOnly = !it.unwatchedOnly) } }
                FilterToggle("Favourites", q.favouritesOnly) { viewModel.update { it.copy(favouritesOnly = !it.favouritesOnly) } }
                if (state.genres.isNotEmpty()) {
                    OutlinedButton(onClick = { showGenres = true }) {
                        Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(q.genre ?: "Genre")
                    }
                }
            }
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

    if (showSort) {
        SortDialog(
            query = state.query,
            onSort = viewModel::sortBy,
            onDismiss = { showSort = false }
        )
    }
    if (showGenres) {
        GenreDialog(
            genres = state.genres,
            selected = state.query.genre,
            onSelect = { genre ->
                viewModel.update { it.copy(genre = genre) }
                showGenres = false
            },
            onDismiss = { showGenres = false }
        )
    }
}

@Composable
private fun FilterToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) {
            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun LetterRail(letters: Map<Char, Int>, onLetter: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.width(44.dp).fillMaxHeight(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(letters.entries.toList(), key = { it.key }) { (letter, index) ->
            Surface(
                onClick = { onLetter(index) },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(letter.toString(), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun FocusFirst(requester: FocusRequester) {
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo.isWindowFocused) {
        if (!windowInfo.isWindowFocused) return@LaunchedEffect
        repeat(30) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
}

@Composable
private fun SortDialog(query: LibraryQuery, onSort: (LibraryQuery.Sort) -> Unit, onDismiss: () -> Unit) {
    val current = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FocusFirst(current)
        Surface(
            modifier = Modifier.width(460.dp),
            shape = ApertureTheme.shapes.dialog,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(ApertureTheme.spacing.extraLarge)) {
                Text("Sort by", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Choose the current order again to reverse it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(ApertureTheme.spacing.medium))
                LibraryQuery.Sort.entries.forEach { sort ->
                    val selected = sort == query.sort
                    ListItem(
                        selected = selected,
                        onClick = { onSort(sort) },
                        headlineContent = { Text(sort.label) },
                        trailingContent = if (selected && sort != LibraryQuery.Sort.RANDOM) {
                            {
                                Icon(
                                    if (query.descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                                    contentDescription = if (query.descending) "Descending" else "Ascending"
                                )
                            }
                        } else null,
                        modifier = if (selected) Modifier.focusRequester(current) else Modifier
                    )
                }
                Spacer(Modifier.height(ApertureTheme.spacing.medium))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun GenreDialog(genres: List<String>, selected: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FocusFirst(first)
        Surface(
            modifier = Modifier.width(460.dp).heightIn(max = 600.dp),
            shape = ApertureTheme.shapes.dialog,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(ApertureTheme.spacing.extraLarge)) {
                Text("Genre", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(ApertureTheme.spacing.medium))
                LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(4.dp)) {
                    item(key = "all") {
                        ListItem(
                            selected = selected == null,
                            onClick = { onSelect(null) },
                            headlineContent = { Text("All genres") },
                            modifier = Modifier.focusRequester(first)
                        )
                    }
                    items(genres, key = { it }) { genre ->
                        ListItem(
                            selected = genre == selected,
                            onClick = { onSelect(genre) },
                            headlineContent = { Text(genre) }
                        )
                    }
                }
            }
        }
    }
}
