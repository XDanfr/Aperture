package me.xdan.aperture.ui.artwork

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import me.xdan.aperture.data.local.entity.MediaEntity

@OptIn(ExperimentalCoroutinesApi::class)
internal fun dynamicArtworkSeeds(
    activeMediaId: Flow<Long?>,
    media: Flow<List<MediaEntity>>,
    themeId: Flow<String>,
    seed: (String) -> Flow<Int?>
): Flow<Int?> = combine(activeMediaId, media, themeId) { id, items, theme ->
    if (theme != "dynamic") null else items.firstOrNull { it.id == id }
        ?.let { it.backdropPath?.takeIf(String::isNotBlank) ?: it.posterPath?.takeIf(String::isNotBlank) }
}.distinctUntilChanged().transformLatest { path ->
    if (path == null) {
        emit(null)
    } else {
        delay(140)
        seed(path).collect { accent ->
            currentCoroutineContext().ensureActive()
            // Keep the current palette until newly fetched artwork has a saved seed.
            if (accent != null) emit(accent)
        }
    }
}
