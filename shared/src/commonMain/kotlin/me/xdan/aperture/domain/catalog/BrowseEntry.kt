package me.xdan.aperture.domain.catalog

import me.xdan.aperture.data.local.entity.MediaEntity

/** A title in a mixed list (search, My List): a file on this device or a title on the Jellyfin server. */
sealed interface BrowseEntry {
    val key: String
    val sortTitle: String

    data class Local(val media: MediaEntity) : BrowseEntry {
        override val key get() = "local:${media.id}"
        override val sortTitle get() = media.title
    }

    data class Remote(val item: CatalogItem) : BrowseEntry {
        override val key get() = item.key.stableId
        override val sortTitle get() = item.title
    }
}
