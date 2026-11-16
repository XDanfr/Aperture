package me.xdan.aperture.ui.screen.jellyfin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import me.xdan.aperture.domain.catalog.CatalogItem

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
}

@Composable
fun rememberJellyfinDetails(): JellyfinDetailsState = remember { JellyfinDetailsState() }

/** Shows the details panel while [state] has a title, then puts focus back on the card that opened it. */
@Composable
fun JellyfinDetailsHost(state: JellyfinDetailsState) {
    val item = state.item
    if (item != null) {
        JellyfinDetailsDialog(item, onDismiss = state::close)
    }
    LaunchedEffect(item) {
        if (item != null) return@LaunchedEffect
        val origin = state.origin ?: return@LaunchedEffect
        state.origin = null
        // The dialog's window needs a few frames to go before the card can take focus again.
        repeat(30) {
            withFrameNanos { }
            if (runCatching { origin.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
}
