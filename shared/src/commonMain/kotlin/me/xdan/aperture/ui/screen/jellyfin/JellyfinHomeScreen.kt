package me.xdan.aperture.ui.screen.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import me.xdan.aperture.data.jellyfin.JellyfinCatalog
import me.xdan.aperture.domain.catalog.CatalogItem
import me.xdan.aperture.ui.component.CatalogCard
import me.xdan.aperture.ui.component.expressive.ExpressiveLoadingIndicator
import me.xdan.aperture.ui.theme.ApertureTheme

class JellyfinHomeViewModel(catalog: JellyfinCatalog) : ViewModel() {
    data class Row(val title: String, val items: List<CatalogItem>, val landscape: Boolean = false)
    data class State(val spotlight: CatalogItem? = null, val rows: List<Row>? = null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = combine(catalog.activeAccount, catalog.libraries) { account, libraries -> account to libraries }
        .flatMapLatest { (account, libraries) ->
            if (account == null) return@flatMapLatest flowOf(State(rows = emptyList()))
            // Typed libraries only. Untyped (mixed) folders stay in the sidebar: a JellyBridge Discover
            // library is one, and its requestable placeholders mustn't read as things you have.
            val indexed = libraries.filter { it.collectionType in HOME_ROW_COLLECTIONS }
            val recent = indexed.map { library ->
                catalog.recentlyAdded(account, library.id)
            }
            // Next Up and the spotlight come from the server or a random pick, once per visit.
            val extras = flow {
                emit(Pair(catalog.nextUp(account), catalog.spotlight(account, 1).firstOrNull()))
            }
            combine(listOf(catalog.continueWatching(account)) + recent) { lists -> lists.toList() }
                .combine(extras) { lists, (nextUp, spotlight) ->
                    val rows = buildList {
                        add(Row("Continue Watching", lists[0], landscape = true))
                        add(Row("Next Up", nextUp, landscape = true))
                        indexed.forEachIndexed { index, library ->
                            add(Row("Recently added in ${library.name}", lists[index + 1]))
                        }
                    }.filter { it.items.isNotEmpty() }
                    State(spotlight = spotlight, rows = rows)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    private companion object {
        val HOME_ROW_COLLECTIONS = setOf("movies", "tvshows", "homevideos")
    }
}

/** Home for a profile that uses Jellyfin: a spotlight backdrop, then rows from the server. */
@Composable
fun JellyfinHomeScreen(
    viewModel: JellyfinHomeViewModel,
    onItemClick: (CatalogItem, FocusRequester) -> Unit,
    drawerFocusRequester: FocusRequester? = null,
    contentEntryFocusRequester: FocusRequester = FocusRequester.Default,
    onContentFocused: (FocusRequester) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val rows = state.rows
    if (rows == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoadingIndicator(Modifier.size(48.dp)) }
        return
    }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nothing here yet. Your Jellyfin library is still syncing, or this account can't see any libraries.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large)
    ) {
        state.spotlight?.let { spotlight ->
            item(key = "spotlight") { Spotlight(spotlight) }
        }
        itemsIndexed(rows, key = { _, row -> row.title }) { rowIndex, row ->
            Column {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 48.dp, bottom = ApertureTheme.spacing.small)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large)
                ) {
                    itemsIndexed(row.items, key = { _, item -> item.key.stableId }) { index, item ->
                        CatalogCard(
                            item = item,
                            landscape = row.landscape,
                            onClick = { onItemClick(item, it) },
                            focusRequester = if (rowIndex == 0 && index == 0) contentEntryFocusRequester else null,
                            drawerFocusRequester = if (index == 0) drawerFocusRequester else null,
                            onFocused = onContentFocused,
                            modifier = Modifier.width(if (row.landscape) 280.dp else 150.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Spotlight(item: CatalogItem) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(start = 32.dp, end = 32.dp, top = 24.dp)
            .clip(ApertureTheme.shapes.hero)
    ) {
        item.backdrop?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.92f)))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(32.dp).fillMaxWidth(0.6f)) {
            Text(item.title, style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.overview?.let {
                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
