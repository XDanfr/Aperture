package me.xdan.aperture.ui.screen.library

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.xdan.aperture.data.local.entity.MediaEntity

internal class LibraryContent(
    movies: Flow<List<MediaEntity>>,
    episodes: Flow<List<MediaEntity>>,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    // Start on first use, then keep the latest Room snapshot until the ViewModel clears.
    val movies = movies
        .distinctUntilChanged()
        .flowOn(dispatcher)
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    val showGroups = episodes
        .distinctUntilChanged()
        .map { items ->
            items.groupBy { it.title }
                .map { (title, episodes) ->
                    ShowGroup(
                        title,
                        episodes.sortedWith(
                            compareBy(
                                { it.seasonNumber ?: Int.MAX_VALUE },
                                { it.episodeNumber ?: Int.MAX_VALUE }
                            )
                        )
                    )
                }
                .sortedBy { it.title.lowercase() }
        }
        .flowOn(dispatcher)
        .stateIn(scope, SharingStarted.Lazily, emptyList())
}
