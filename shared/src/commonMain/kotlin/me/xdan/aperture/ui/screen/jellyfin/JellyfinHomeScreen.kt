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

class JellyfinHomeViewModel(
    catalog: JellyfinCatalog,
    homeRows: me.xdan.aperture.domain.sidebar.HomeRowsRepository
) : ViewModel() {
    data class Row(val key: String, val title: String, val items: List<CatalogItem>, val landscape: Boolean = false)
    data class State(
        val spotlight: CatalogItem? = null,
        val rows: List<Row>? = null,
        /** How many rows come before the spotlight, as the profile placed it. */
        val spotlightPosition: Int = 0
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = combine(catalog.activeAccount, catalog.libraries, homeRows.layout) { account, libraries, layout -> Triple(account, libraries, layout) }
        .flatMapLatest { (account, libraries, layout) ->
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
                        add(Row(CONTINUE, "Continue Watching", lists[0], landscape = true))
                        add(Row(NEXT_UP, "Next Up", nextUp, landscape = true))
                        indexed.forEachIndexed { index, library ->
                            add(Row(recentKey(library.id), "Recently added in ${library.name}", lists[index + 1]))
                        }
                    }.associateBy { it.key }
                    // In the profile's order (Settings › Home rows), leaving out hidden and empty rows.
                    val keys = layout.arrange(candidates(libraries).map { it.first })
                    val shown = keys.mapNotNull(rows::get).filter { it.items.isNotEmpty() }
                    val before = keys.takeWhile { it != SPOTLIGHT }.toSet()
                    State(
                        spotlight = spotlight.takeIf { SPOTLIGHT in keys },
                        rows = shown,
                        spotlightPosition = shown.count { it.key in before }
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    companion object {
        private val HOME_ROW_COLLECTIONS = setOf("movies", "tvshows", "homevideos")
        const val SPOTLIGHT = "spotlight"
        const val CONTINUE = "continue"
        const val NEXT_UP = "next_up"
        fun recentKey(libraryId: String) = "recent:$libraryId"

        /** Every row Home can show, as (key, label), in default order. */
        fun candidates(libraries: List<me.xdan.aperture.data.local.entity.RemoteLibraryEntity>): List<Pair<String, String>> = buildList {
            add(SPOTLIGHT to "Spotlight")
            add(CONTINUE to "Continue Watching")
            add(NEXT_UP to "Next Up")
            libraries.filter { it.collectionType in HOME_ROW_COLLECTIONS }
                .forEach { add(recentKey(it.id) to "Recently added in ${it.name}") }
        }
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
    // Home is where the app opens, so it takes focus itself once the rows are there, like the
    // local Home; otherwise a remote has nothing to move from.
    LaunchedEffect(Unit) {
        repeat(30) {
            withFrameNanos { }
            if (runCatching { contentEntryFocusRequester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large)
    ) {
        val spotlight = state.spotlight
        val spotlightAt = state.spotlightPosition.coerceIn(0, rows.size)
        rows.forEachIndexed { rowIndex, row ->
            if (spotlight != null && rowIndex == spotlightAt) item(key = "spotlight") { Spotlight(spotlight) }
            item(key = row.key) {
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
        if (spotlight != null && spotlightAt == rows.size) item(key = "spotlight") { Spotlight(spotlight) }
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
