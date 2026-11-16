package me.xdan.aperture.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import kotlinx.coroutines.launch
import me.xdan.aperture.domain.catalog.LibraryQuery
import me.xdan.aperture.ui.platform.Dialog
import me.xdan.aperture.ui.theme.ApertureTheme

/** Which sorts and filters a library can offer; local files have no ratings or genres, for example. */
data class LibraryOptions(
    val sorts: List<LibraryQuery.Sort> = LibraryQuery.Sort.entries,
    val genres: List<String> = emptyList(),
    val decades: List<Int> = emptyList(),
    val offerUhd: Boolean = false
)

/**
 * The row above a library grid: sort, then filters. [firstModifier] goes on the sort button, so
 * the screen can give it a focus requester and a way back to the sidebar.
 */
@Composable
fun LibraryControlsRow(
    query: LibraryQuery,
    options: LibraryOptions,
    onSort: (LibraryQuery.Sort) -> Unit,
    onUpdate: ((LibraryQuery) -> LibraryQuery) -> Unit,
    firstModifier: Modifier = Modifier
) {
    var showSort by remember { mutableStateOf(false) }
    var showGenres by remember { mutableStateOf(false) }
    var showDecades by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small)) {
        OutlinedButton(onClick = { showSort = true }, modifier = firstModifier) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(query.sort.label)
            if (query.sort != LibraryQuery.Sort.RANDOM) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (query.descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                    contentDescription = if (query.descending) "Descending" else "Ascending",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        FilterToggle("Unwatched", query.unwatchedOnly) { onUpdate { it.copy(unwatchedOnly = !it.unwatchedOnly) } }
        FilterToggle("Favourites", query.favouritesOnly) { onUpdate { it.copy(favouritesOnly = !it.favouritesOnly) } }
        if (options.offerUhd) {
            FilterToggle("4K", query.uhdOnly) { onUpdate { it.copy(uhdOnly = !it.uhdOnly) } }
        }
        if (options.genres.isNotEmpty() || query.genre != null) {
            PickerButton(Icons.Rounded.FilterList, query.genre ?: "Genre", query.genre != null) { showGenres = true }
        }
        if (options.decades.size > 1 || query.decade != null) {
            PickerButton(
                Icons.Rounded.CalendarMonth,
                query.decade?.let(LibraryQuery::decadeLabel) ?: "Decade",
                query.decade != null
            ) { showDecades = true }
        }
    }

    if (showSort) {
        SortDialog(query, options.sorts, onSort = onSort, onDismiss = { showSort = false })
    }
    if (showGenres) {
        PickerDialog(
            title = "Genre",
            allLabel = "All genres",
            options = options.genres.map { it to it },
            selected = query.genre,
            onSelect = { genre ->
                onUpdate { it.copy(genre = genre) }
                showGenres = false
            },
            onDismiss = { showGenres = false }
        )
    }
    if (showDecades) {
        PickerDialog(
            title = "Decade",
            allLabel = "Any decade",
            options = options.decades.map { it to LibraryQuery.decadeLabel(it) },
            selected = query.decade,
            onSelect = { decade ->
                onUpdate { it.copy(decade = decade) }
                showDecades = false
            },
            onDismiss = { showDecades = false }
        )
    }
}

/**
 * Chips keep the same element when toggled, so focus stays put; swapping an outlined button for
 * a filled one would drop it into the grid.
 */
@Composable
private fun FilterToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null
    ) { Text(label) }
}

@Composable
private fun PickerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = active,
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
    ) { Text(label) }
}

/** The A–Z rail beside a title-sorted grid. Only letters that have titles appear. */
@Composable
fun LetterRail(letters: Map<Char, Int>, onLetter: (Int) -> Unit) {
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
                    containerColor = Color.Transparent,
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

/** Focuses [requester] once the dialog's window has focus. */
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
private fun SortDialog(
    query: LibraryQuery,
    sorts: List<LibraryQuery.Sort>,
    onSort: (LibraryQuery.Sort) -> Unit,
    onDismiss: () -> Unit
) {
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
                Text("Choose the current order again to reverse it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(ApertureTheme.spacing.medium))
                sorts.forEach { sort ->
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
private fun <T> PickerDialog(
    title: String,
    allLabel: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    onDismiss: () -> Unit
) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FocusFirst(first)
        Surface(
            modifier = Modifier.width(460.dp).heightIn(max = 600.dp),
            shape = ApertureTheme.shapes.dialog,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(ApertureTheme.spacing.extraLarge)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(ApertureTheme.spacing.medium))
                LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(4.dp)) {
                    item(key = "all") {
                        ListItem(
                            selected = selected == null,
                            onClick = { onSelect(null) },
                            headlineContent = { Text(allLabel) },
                            modifier = Modifier.focusRequester(first)
                        )
                    }
                    items(options, key = { it.second }) { (value, label) ->
                        ListItem(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                            headlineContent = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Moves a grid to a letter: scrolls to the position, then focuses that card once it's composed.
 * Cards take their requester from [requesterFor].
 */
@Stable
class GridJump internal constructor(
    private val grid: androidx.compose.foundation.lazy.grid.LazyGridState,
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    private val requesters = mutableMapOf<Int, FocusRequester>()
    internal var pending by mutableStateOf<Int?>(null)

    /** The first card keeps the screen's entry requester, so the sidebar's Right still lands on it. */
    fun requesterFor(index: Int, entry: FocusRequester): FocusRequester =
        if (index == 0) entry.also { requesters[0] = it } else requesters.getOrPut(index) { FocusRequester() }

    fun jumpTo(index: Int) {
        scope.launch {
            grid.scrollToItem(index)
            pending = index
        }
    }

    internal fun requester(index: Int) = requesters[index]
}

@Composable
fun rememberGridJump(grid: androidx.compose.foundation.lazy.grid.LazyGridState): GridJump {
    val scope = rememberCoroutineScope()
    val jump = remember(grid) { GridJump(grid, scope) }
    LaunchedEffect(jump.pending) {
        val index = jump.pending ?: return@LaunchedEffect
        repeat(20) {
            withFrameNanos { }
            if (runCatching { jump.requester(index)?.requestFocus() }.getOrNull() == true) {
                jump.pending = null
                return@LaunchedEffect
            }
        }
        jump.pending = null
    }
    return jump
}
