package me.xdan.aperture.ui.screen.jellyfin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.xdan.aperture.data.jellyfin.isDiscover
import androidx.compose.ui.focus.FocusRequester
import me.xdan.aperture.domain.catalog.CatalogItem
import me.xdan.aperture.domain.model.MediaKey

/** Which Jellyfin title's panel is open, and the card to return focus to when it closes. */
@Stable
class JellyfinDetailsState {
    var item by mutableStateOf<CatalogItem?>(null)
        private set
    internal var origin: FocusRequester? = null

    fun open(item: CatalogItem, from: FocusRequester?) {
        origin = from
        this.item = item
    }

    internal fun close() {
        item = null
    }

    /** A long-pressed card's menu. */
    data class Menu(val item: CatalogItem, val opensToRight: Boolean)

    var menu by mutableStateOf<Menu?>(null)
        private set

    fun openMenu(item: CatalogItem, from: FocusRequester?, opensToRight: Boolean) {
        origin = from
        menu = Menu(item, opensToRight)
    }

    internal fun closeMenu() {
        menu = null
    }
}

@Composable
fun rememberJellyfinDetails(): JellyfinDetailsState = remember { JellyfinDetailsState() }

/** Shows the details panel while [state] has a title, then puts focus back on the card that opened it. */
@Composable
fun JellyfinDetailsHost(state: JellyfinDetailsState, onPlay: (MediaKey.Jellyfin, Boolean) -> Unit) {
    state.menu?.let { menu -> JellyfinContextMenu(state, menu, onPlay) }
    val item = state.item
    if (item != null) {
        JellyfinDetailsDialog(
            item,
            onDismiss = state::close,
            // The panel closes first: it's a window of its own and would stay above the player.
            onPlay = { key, fromStart ->
                state.close()
                onPlay(key, fromStart)
            }
        )
    }
    LaunchedEffect(item, state.menu) {
        if (item != null || state.menu != null) return@LaunchedEffect
        val origin = state.origin ?: return@LaunchedEffect
        state.origin = null
        // The dialog's window needs a few frames to go before the card can take focus again.
        repeat(30) {
            withFrameNanos { }
            if (runCatching { origin.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
}

/** The long-press menu for a Jellyfin title, with the same entries as a local one's where they apply. */
@Composable
private fun JellyfinContextMenu(state: JellyfinDetailsState, menu: JellyfinDetailsState.Menu, onPlay: (MediaKey.Jellyfin, Boolean) -> Unit) {
    val catalog: me.xdan.aperture.data.jellyfin.JellyfinCatalog = org.koin.compose.koinInject()
    val libraries by catalog.libraries.collectAsState(emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val item = menu.item
    val key = item.key as MediaKey.Jellyfin
    val discover = libraries.firstOrNull { it.id == item.libraryId }?.isDiscover == true
    fun act(block: suspend (me.xdan.aperture.data.local.entity.JellyfinAccountEntity) -> Unit) {
        state.closeMenu()
        scope.launch { catalog.activeAccount.first()?.let { block(it) } }
    }
    me.xdan.aperture.ui.component.TitleContextMenu(
        title = item.title,
        key = item.key.stableId,
        opensToRight = menu.opensToRight,
        onDismiss = state::closeMenu,
        actions = buildList {
            if (item.progress > 0f) {
                add(me.xdan.aperture.ui.component.ContextMenuAction("Remove from Continue Watching", androidx.compose.material.icons.Icons.Rounded.RemoveCircle) {
                    act { catalog.clearResume(it, key.itemId) }
                })
            }
            add(me.xdan.aperture.ui.component.ContextMenuAction("Info", androidx.compose.material.icons.Icons.Rounded.Info) {
                state.closeMenu()
                state.open(item, state.origin)
            })
            if (discover) {
                add(
                    me.xdan.aperture.ui.component.ContextMenuAction(
                        if (item.isFavorite) "Cancel download" else "Download",
                        if (item.isFavorite) androidx.compose.material.icons.Icons.Rounded.DownloadDone else androidx.compose.material.icons.Icons.Rounded.Download
                    ) { act { catalog.setFavorite(it, key.itemId, !item.isFavorite) } }
                )
            } else {
                if (item.progress > 0f && item.kind != CatalogItem.Kind.SHOW) {
                    add(me.xdan.aperture.ui.component.ContextMenuAction("Play from beginning", androidx.compose.material.icons.Icons.Rounded.Replay) {
                        state.closeMenu()
                        state.origin = null
                        onPlay(key, true)
                    })
                }
                add(
                    me.xdan.aperture.ui.component.ContextMenuAction(
                        if (item.isFavorite) "Remove from My List" else "Add to My List",
                        if (item.isFavorite) androidx.compose.material.icons.Icons.Rounded.PlaylistRemove else androidx.compose.material.icons.Icons.AutoMirrored.Rounded.PlaylistAdd
                    ) { act { catalog.setFavorite(it, key.itemId, !item.isFavorite) } }
                )
                add(
                    me.xdan.aperture.ui.component.ContextMenuAction(
                        if (item.played) "Mark as unwatched" else "Mark as watched",
                        if (item.played) androidx.compose.material.icons.Icons.Rounded.RadioButtonUnchecked else androidx.compose.material.icons.Icons.Rounded.CheckCircle
                    ) { act { catalog.setPlayed(it, key.itemId, !item.played) } }
                )
            }
        }
    )
}
