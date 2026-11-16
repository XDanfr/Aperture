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
import me.xdan.aperture.companion.protocol.CompanionMedia
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

@Composable
fun MoviesScreen(library: Library, contentPadding: PaddingValues, onOpen: (LibraryEntry) -> Unit) {
    var sort by rememberSaveable { mutableStateOf(MovieSort.TITLE) }
    val movies = remember(library, sort) {
        when (sort) {
            MovieSort.TITLE -> library.movies
            MovieSort.RECENT -> library.movies.sortedByDescending { it.dateAdded }
            MovieSort.YEAR -> library.movies.sortedByDescending { it.year ?: 0 }
        }
    }
    PosterGrid(
        title = "Movies",
        contentPadding = contentPadding,
        empty = movies.isEmpty(),
        emptyMessage = "No movies yet. Movies your ${LocalDeviceNoun.current} finds will appear here.",
        header = {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MovieSort.entries.forEach {
                    FilterChip(selected = sort == it, onClick = { sort = it }, label = { Text(it.label) })
                }
            }
        }
    ) {
        items(movies, key = { it.id }) { media ->
            PosterCard(
                title = media.title,
                posterPath = media.posterPath,
                subtitle = media.year?.toString(),
                progress = media.progressFraction.takeIf { it > 0f && !media.completed },
                onClick = { onOpen(LibraryEntry.Movie(media)) }
            )
        }
    }
}

@Composable
fun ShowsScreen(library: Library, contentPadding: PaddingValues, onOpen: (LibraryEntry) -> Unit) {
    PosterGrid(
        title = "TV shows",
        contentPadding = contentPadding,
        empty = library.shows.isEmpty(),
        emptyMessage = "No shows yet. Episodes your ${LocalDeviceNoun.current} finds will appear here, grouped by show."
    ) {
        items(library.shows, key = { it.key }) { show ->
            PosterCard(
                title = show.title,
                posterPath = show.posterPath,
                subtitle = "${show.episodes.size} episode${if (show.episodes.size == 1) "" else "s"}",
                onClick = { onOpen(LibraryEntry.Show(show)) }
            )
        }
    }
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
