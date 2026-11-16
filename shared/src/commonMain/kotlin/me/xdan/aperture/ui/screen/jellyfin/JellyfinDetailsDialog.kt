package me.xdan.aperture.ui.screen.jellyfin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import me.xdan.aperture.domain.catalog.CatalogItem
import me.xdan.aperture.ui.component.ArtworkFallback
import me.xdan.aperture.ui.component.MediaInfoDialog
import me.xdan.aperture.ui.platform.Dialog
import me.xdan.aperture.ui.platform.edgeToEdgeDialogProperties
import me.xdan.aperture.ui.platform.onBackgroundClick
import me.xdan.aperture.ui.theme.GlassBackground
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A Jellyfin title's details, in the same side panel as local titles. Shows pick an episode;
 * My List and Watched change the server's state. Playback arrives with the player step.
 */
@Composable
fun JellyfinDetailsDialog(
    item: CatalogItem,
    onDismiss: () -> Unit,
    /** Plays the title (or a show's chosen episode); true starts from the beginning. */
    onPlay: (me.xdan.aperture.domain.model.MediaKey.Jellyfin, Boolean) -> Unit
) {
    val viewModel: JellyfinDetailsViewModel = koinViewModel(key = "details:${item.key.stableId}") {
        parametersOf(item.jellyfinId)
    }
    val state by viewModel.state.collectAsState()
    val info by viewModel.info.collectAsState()
    val message by viewModel.message.collectAsState()
    // The index's copy once it loads, so toggles show at once; the clicked card until then.
    val shown = state.item ?: item
    val target = state.target ?: shown
    var showEpisodes by remember { mutableStateOf(false) }
    val firstAction = remember { FocusRequester() }
    val episodeButton = remember { FocusRequester() }
    var returnToEpisodeButton by remember { mutableStateOf(false) }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3_000)
            viewModel.clearMessage()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = edgeToEdgeDialogProperties()) {
        val windowInfo = LocalWindowInfo.current
        LaunchedEffect(windowInfo.isWindowFocused) {
            if (!windowInfo.isWindowFocused) return@LaunchedEffect
            repeat(30) {
                withFrameNanos { }
                if (runCatching { firstAction.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).onBackgroundClick(onClick = onDismiss)) {
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInHorizontally(tween(300)) { it } + fadeIn(tween(200)),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(500.dp),
                    colors = SurfaceDefaults.colors(containerColor = GlassBackground),
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(32.dp)) {
                        val isShow = shown.kind == CatalogItem.Kind.SHOW
                        val artwork = if (shown.kind == CatalogItem.Kind.EPISODE) shown.thumb ?: shown.backdrop else shown.backdrop
                        val artworkHeight = if (isShow) 120.dp else 160.dp
                        if (artwork == null) {
                            ArtworkFallback(
                                title = shown.title,
                                isFocused = false,
                                modifier = Modifier.fillMaxWidth().height(artworkHeight).clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            AsyncImage(
                                model = artwork,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(artworkHeight).clip(RoundedCornerShape(12.dp))
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            shown.episodeTitle ?: shown.title,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(subheading(shown), style = MaterialTheme.typography.bodyLarge, color = Color.Gray, maxLines = 1)
                        if (shown.genres.isNotEmpty()) {
                            Text(shown.genres.take(4).joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = Color.Gray, maxLines = 1)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            (if (isShow) state.episode?.overview else null) ?: shown.overview ?: "No synopsis available.",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (isShow) 3 else 5,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.weight(1f))

                        if (isShow && state.episodes.isNotEmpty()) {
                            val episode = state.episode
                            OutlinedButton(
                                onClick = {
                                    returnToEpisodeButton = true
                                    showEpisodes = true
                                },
                                modifier = Modifier.fillMaxWidth().focusRequester(episodeButton)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(episode?.episodeLabel ?: "Choose episode", style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        episode?.episodeTitle ?: "Choose episode",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (episode?.played == true) Icon(Icons.Rounded.Check, contentDescription = "Watched", modifier = Modifier.size(18.dp))
                                Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Choose episode")
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(
                            modifier = Modifier.heightIn(min = 52.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // A show plays its chosen episode; until the episodes load, there's nothing to play.
                            val playable = if (isShow) state.episode else target
                            val inProgress = (playable?.progress ?: 0f) > 0f
                            Button(
                                onClick = { playable?.let { onPlay(it.key as me.xdan.aperture.domain.model.MediaKey.Jellyfin, false) } },
                                enabled = playable != null,
                                modifier = Modifier.focusRequester(firstAction)
                            ) {
                                Icon(if (playable?.played == true && !inProgress) Icons.Rounded.Replay else Icons.Rounded.PlayArrow, null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when {
                                        inProgress -> "Continue"
                                        playable?.played == true -> "Rewatch"
                                        else -> "Play"
                                    }
                                )
                            }
                            if (inProgress) {
                                IconButton(onClick = { playable?.let { onPlay(it.key as me.xdan.aperture.domain.model.MediaKey.Jellyfin, true) } }) {
                                    Icon(Icons.Rounded.Replay, contentDescription = "Play from the beginning")
                                }
                            }
                            IconButton(onClick = viewModel::toggleFavorite) {
                                Icon(
                                    if (shown.isFavorite) Icons.AutoMirrored.Rounded.PlaylistAddCheck else Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    contentDescription = if (shown.isFavorite) "Remove from My List" else "Add to My List"
                                )
                            }
                            val watchedLabel = when {
                                isShow && target.played -> "Mark episode unwatched"
                                isShow -> "Mark episode watched"
                                target.played -> "Mark unwatched"
                                else -> "Mark watched"
                            }
                            IconButton(onClick = viewModel::togglePlayed) {
                                Icon(
                                    if (target.played) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = watchedLabel
                                )
                            }
                            IconButton(onClick = viewModel::loadInfo) {
                                Icon(Icons.Rounded.Info, contentDescription = "Info")
                            }
                        }
                    }
                }
            }
        }

        // Back on the episode button once the picker closes, rather than the first action.
        LaunchedEffect(showEpisodes) {
            if (showEpisodes || !returnToEpisodeButton) return@LaunchedEffect
            returnToEpisodeButton = false
            repeat(30) {
                withFrameNanos { }
                if (runCatching { episodeButton.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            }
        }
        if (showEpisodes) {
            EpisodePicker(
                episodes = state.episodes,
                selected = state.episode,
                onSelect = {
                    viewModel.selectEpisode(it)
                    showEpisodes = false
                },
                onDismiss = { showEpisodes = false }
            )
        }
        info?.let { current ->
            MediaInfoDialog(
                title = target.episodeLabel?.let { "${target.title} · $it" } ?: target.title,
                info = (current as? JellyfinDetailsViewModel.InfoState.Ready)?.info,
                error = "Couldn't load the details from the server.".takeIf { current is JellyfinDetailsViewModel.InfoState.Failed },
                onDismiss = viewModel::dismissInfo
            )
        }
    }
}

private fun subheading(item: CatalogItem): String = listOfNotNull(
    if (item.kind == CatalogItem.Kind.EPISODE) listOfNotNull(item.title, item.episodeLabel).joinToString(" · ") else null,
    item.year?.toString().takeIf { item.kind != CatalogItem.Kind.EPISODE },
    item.runtimeMs?.takeIf { it > 0 && item.kind != CatalogItem.Kind.SHOW }?.let(::runtimeLabel),
    item.officialRating,
    item.rating?.let { "★ %.1f".format(it) },
    "Watched".takeIf { item.played && item.kind != CatalogItem.Kind.SHOW }
).joinToString("  ·  ")

private fun runtimeLabel(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes >= 60 -> "${minutes / 60} hr ${minutes % 60} min"
        minutes == 0L -> "Under 1 min"
        else -> "$minutes min"
    }
}

@Composable
private fun EpisodePicker(
    episodes: List<CatalogItem>,
    selected: CatalogItem?,
    onSelect: (CatalogItem) -> Unit,
    onDismiss: () -> Unit
) {
    val seasons = remember(episodes) { episodes.map { it.seasonNumber ?: 0 }.distinct().sorted() }
    var season by remember(episodes) { mutableStateOf(selected?.seasonNumber ?: seasons.firstOrNull() ?: 0) }
    val seasonEpisodes = episodes.filter { (it.seasonNumber ?: 0) == season }
    val entryIndex = seasonEpisodes.indexOfFirst { it.key == selected?.key }.coerceAtLeast(0)
    val entry = remember { FocusRequester() }
    val listState = rememberLazyListState()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(season, seasonEpisodes.size) {
            if (seasonEpisodes.isEmpty()) return@LaunchedEffect
            listState.scrollToItem(entryIndex)
            repeat(30) {
                withFrameNanos { }
                if (runCatching { entry.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            }
        }
        Surface(
            modifier = Modifier.width(780.dp).height(650.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(28.dp)) {
                Text("Choose an episode", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                if (seasons.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(seasons, key = { it }) { number ->
                            Surface(
                                onClick = { season = number },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (number == season) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(if (number == 0) "Specials" else "Season $number", modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(seasonEpisodes, key = { it.key.stableId }) { episode ->
                        val isSelected = episode.key == selected?.key
                        Surface(
                            onClick = { onSelect(episode) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (episode == seasonEpisodes.getOrNull(entryIndex)) Modifier.focusRequester(entry) else Modifier),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                episode.thumb?.let { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.width(132.dp).height(74.dp).clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(Modifier.width(14.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Episode ${episode.episodeNumber ?: "?"}", style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        episode.episodeTitle ?: "Episode ${episode.episodeNumber ?: "?"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        episode.overview ?: "No synopsis available.",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (episode.played) {
                                    Spacer(Modifier.width(10.dp))
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Watched", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}
