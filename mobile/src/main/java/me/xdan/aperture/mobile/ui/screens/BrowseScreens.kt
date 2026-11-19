package me.xdan.aperture.mobile.ui.screens

import me.xdan.aperture.mobile.ui.components.LocalDeviceNoun
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.xdan.aperture.companion.protocol.CollectionKind
import me.xdan.aperture.companion.protocol.CompanionMedia
import me.xdan.aperture.mobile.ui.components.CollectionIcons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import me.xdan.aperture.mobile.data.Library
import me.xdan.aperture.mobile.data.LibraryEntry
import me.xdan.aperture.mobile.data.episodeLabel
import me.xdan.aperture.mobile.data.progressFraction
import me.xdan.aperture.mobile.ui.components.Artwork
import me.xdan.aperture.mobile.ui.components.EmptyState
import me.xdan.aperture.mobile.ui.components.PosterCard
import me.xdan.aperture.mobile.ui.components.PosterGridMinSize
import me.xdan.aperture.mobile.ui.components.PosterRow
import me.xdan.aperture.mobile.ui.components.SectionTitle
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

enum class MovieSort(val label: String) { TITLE("A–Z"), RECENT("Recently added"), YEAR("Year") }

/**
 * The Library tab: the TV's library entries (its sidebar, in its order and with its icons) and
 * My List, with search at the top. Each opens its own grid; Back returns here.
 */
@Composable
fun LibraryScreen(library: Library, contentPadding: PaddingValues, onOpen: (LibraryEntry) -> Unit) {
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }
    when (val current = page) {
        null -> LibraryIndex(library, contentPadding, onSelect = { page = it })
        PAGE_SEARCH -> SearchScreen(library, contentPadding, onOpen)
        PAGE_MY_LIST -> EntryGrid("My List", library.favourites, contentPadding, onOpen,
            emptyMessage = "Add titles to My List from their details, here or on your ${LocalDeviceNoun.current}.")
        else -> {
            val collection = library.collections.firstOrNull { it.key == current }
            EntryGrid(
                collection?.name ?: "Library",
                library.entriesIn(current),
                contentPadding,
                onOpen,
                emptyMessage = "Nothing here yet. Titles your ${LocalDeviceNoun.current} finds will appear here."
            )
        }
    }
}

private const val PAGE_SEARCH = "page:search"
private const val PAGE_MY_LIST = "page:my_list"

@Composable
private fun LibraryIndex(library: Library, contentPadding: PaddingValues, onSelect: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Library", style = MaterialTheme.typography.headlineMedium)
                androidx.compose.material3.Surface(
                    onClick = { onSelect(PAGE_SEARCH) },
                    shape = SearchBarDefaults.inputFieldShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                        Text("Search your library", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        // Discover has its own tab.
        val entries = library.collections.filter { it.kind != CollectionKind.DISCOVER }
        items(entries.size) { index ->
            val collection = entries[index]
            LibraryRow(
                icon = CollectionIcons.forId(collection.icon),
                name = collection.name,
                count = library.count(collection.key),
                onClick = { onSelect(collection.key) }
            )
        }
        item {
            LibraryRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                name = "My List",
                count = library.favourites.size,
                onClick = { onSelect(PAGE_MY_LIST) }
            )
        }
    }
}

@Composable
private fun LibraryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, count: Int, onClick: () -> Unit) {
    androidx.compose.material3.ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(if (count == 1) "1 title" else "$count titles") },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp)
    )
}

/** Discover: titles that can be requested. Selecting one offers Download. */
@Composable
fun DiscoverScreen(library: Library, contentPadding: PaddingValues, onOpen: (LibraryEntry) -> Unit) {
    EntryGrid(
        "Discover",
        library.discover,
        contentPadding,
        onOpen,
        emptyMessage = "Nothing to discover right now.",
        sortable = false,
        subtitle = { entry -> if ((entry as? LibraryEntry.Movie)?.media?.isFavorite == true) "Requested" else (entry as? LibraryEntry.Movie)?.media?.year?.toString() }
    )
}

/** A grid of films and shows, sortable by title, date added or year. */
@Composable
private fun EntryGrid(
    title: String,
    entries: List<LibraryEntry>,
    contentPadding: PaddingValues,
    onOpen: (LibraryEntry) -> Unit,
    emptyMessage: String,
    sortable: Boolean = true,
    subtitle: (LibraryEntry) -> String? = { entry ->
        when (entry) {
            is LibraryEntry.Movie -> entry.media.year?.toString()
            is LibraryEntry.Show -> "${entry.group.episodes.size} episode${if (entry.group.episodes.size == 1) "" else "s"}"
        }
    }
) {
    var sort by rememberSaveable { mutableStateOf(MovieSort.TITLE) }
    val sorted = remember(entries, sort) {
        when (sort) {
            MovieSort.TITLE -> entries.sortedBy { it.title.lowercase() }
            MovieSort.RECENT -> entries.sortedByDescending { it.dateAdded }
            MovieSort.YEAR -> entries.sortedByDescending { it.year ?: 0 }
        }
    }
    PosterGrid(
        title = title,
        contentPadding = contentPadding,
        empty = entries.isEmpty(),
        emptyMessage = emptyMessage,
        header = if (!sortable) null else { {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MovieSort.entries.forEach {
                    FilterChip(selected = sort == it, onClick = { sort = it }, label = { Text(it.label) })
                }
            }
        } }
    ) {
        items(sorted, key = { it.key }) { entry ->
            PosterCard(
                title = entry.title,
                posterPath = entry.posterPath,
                subtitle = subtitle(entry),
                progress = (entry as? LibraryEntry.Movie)?.media?.let { m -> m.progressFraction.takeIf { it > 0f && !m.completed } },
                onClick = { onOpen(entry) }
            )
        }
    }
}

private val LibraryEntry.dateAdded: Long
    get() = when (this) {
        is LibraryEntry.Movie -> media.dateAdded
        is LibraryEntry.Show -> group.episodes.maxOf { it.dateAdded }
    }

private val LibraryEntry.year: Int?
    get() = when (this) {
        is LibraryEntry.Movie -> media.year
        is LibraryEntry.Show -> group.year
    }

@Composable
private fun PosterGrid(
    title: String,
    contentPadding: PaddingValues,
    empty: Boolean,
    emptyMessage: String,
    header: (@Composable () -> Unit)? = null,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(PosterGridMinSize),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                header?.invoke()
            }
        }
        if (empty) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Nothing here yet", emptyMessage) }
        } else {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(library: Library, contentPadding: PaddingValues, onOpen: (LibraryEntry) -> Unit) {
    val state = rememberTextFieldState()
    val query = state.text.toString()
    val results = remember(library, query) { library.search(query) }
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        SearchBarDefaults.InputField(
            state = state,
            onSearch = {},
            expanded = false,
            onExpandedChange = {},
            placeholder = { Text("Search your library") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(SearchBarDefaults.inputFieldShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        when {
            query.isBlank() -> EmptyState("Find something to watch", "Search movies, shows and episode titles on your ${LocalDeviceNoun.current}.")
            results.isEmpty() -> EmptyState("No matches", "Nothing in your library matches “$query”.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(PosterGridMinSize),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, contentPadding.calculateBottomPadding() + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(results, key = { it.key }) { entry ->
                    PosterCard(
                        title = entry.title,
                        posterPath = entry.posterPath,
                        subtitle = when (entry) {
                            is LibraryEntry.Movie -> entry.media.year?.toString() ?: "Movie"
                            is LibraryEntry.Show -> "TV show"
                        },
                        onClick = { onOpen(entry) }
                    )
                }
            }
        }
        Spacer(Modifier.height(contentPadding.calculateBottomPadding()))
    }
}
