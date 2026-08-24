package me.xdan.aperture.ui.screen.home

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items as composeItems
import androidx.compose.runtime.Composable

/** Disambiguates the List<HomeRow> overload from LazyColumn's items(count) overload. */
internal fun LazyListScope.items(
    items: List<HomeRow>,
    key: (HomeRow) -> Any,
    itemContent: @Composable LazyItemScope.(HomeRow) -> Unit,
) {
    composeItems(
        items = items,
        key = key,
        itemContent = itemContent,
    )
}
