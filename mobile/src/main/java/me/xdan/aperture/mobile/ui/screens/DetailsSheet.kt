package me.xdan.aperture.mobile.ui.screens

import me.xdan.aperture.mobile.ui.components.LocalDeviceNoun
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.xdan.aperture.companion.protocol.CompanionMedia
import me.xdan.aperture.mobile.data.LibraryEntry
import me.xdan.aperture.mobile.data.ShowGroup
import me.xdan.aperture.mobile.data.episodeLabel
import me.xdan.aperture.mobile.data.isInProgress
import me.xdan.aperture.mobile.data.progressFraction
import me.xdan.aperture.mobile.ui.components.Artwork
import me.xdan.aperture.mobile.ui.components.CompanionShapes
import me.xdan.aperture.mobile.ui.components.DotSeparatedRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(
    entry: LibraryEntry,
    canControlTv: Boolean,
    onDismiss: () -> Unit,
    onPlay: (CompanionMedia, fromStart: Boolean) -> Unit,
    onFavourite: (CompanionMedia, Boolean) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = entry is LibraryEntry.Show),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        when (entry) {
            is LibraryEntry.Movie -> MovieDetails(entry.media, canControlTv, onPlay, onFavourite)
            is LibraryEntry.Show -> ShowDetails(entry.group, canControlTv, onPlay, onFavourite)
        }
    }
}

@Composable
private fun Backdrop(path: String?, poster: String?, title: String) {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(CompanionShapes.hero)
    ) {
        Artwork(path ?: poster, size = "w780", contentDescription = title, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))
            )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayActions(
    media: CompanionMedia,
    canControlTv: Boolean,
    onPlay: (CompanionMedia, Boolean) -> Unit,
    favourite: Boolean,
    onFavourite: (Boolean) -> Unit,
    playLabel: String = if (media.isInProgress) "Resume on ${LocalDeviceNoun.current}" else "Play on ${LocalDeviceNoun.current}"
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // M3 Expressive medium button: the primary action on this sheet.
        Button(
            onClick = { onPlay(media, false) },
            enabled = canControlTv,
            modifier = Modifier.weight(1f).height(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.MediumIconSize))
            Spacer(Modifier.width(8.dp))
            Text(playLabel, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
        if (media.isInProgress) {
            OutlinedButton(
                onClick = { onPlay(media, true) },
                enabled = canControlTv,
                modifier = Modifier.height(ButtonDefaults.MediumContainerHeight)
            ) {
                Icon(Icons.Rounded.Replay, contentDescription = "Play from start")
            }
        }
        FilledTonalIconToggleButton(
            checked = favourite,
            onCheckedChange = onFavourite,
            enabled = canControlTv,
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize())
        ) {
            Icon(
                if (favourite) Icons.Rounded.PlaylistAddCheck else Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = if (favourite) "Remove from My List" else "Add to My List"
            )
        }
    }
}

@Composable
private fun MovieDetails(
    media: CompanionMedia,
    canControlTv: Boolean,
    onPlay: (CompanionMedia, Boolean) -> Unit,
    onFavourite: (CompanionMedia, Boolean) -> Unit
) {
    Column(
        Modifier.navigationBarsPadding().padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Backdrop(media.backdropPath, media.posterPath, media.title)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(media.title, style = MaterialTheme.typography.headlineMedium)
            DotSeparatedRow(listOfNotNull(media.year?.toString(), media.durationMs?.let(::formatRuntime)))
        }
        if (media.isInProgress) {
            LinearWavyProgressIndicator(
                progress = { media.progressFraction },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
        PlayActions(media, canControlTv, onPlay, media.isFavorite, { onFavourite(media, it) })
        media.overview?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun ShowDetails(
    show: ShowGroup,
    canControlTv: Boolean,
    onPlay: (CompanionMedia, Boolean) -> Unit,
    onFavourite: (CompanionMedia, Boolean) -> Unit
) {
    val seasons = show.seasons
    val nextUp = show.nextUp
    var selectedSeason by remember(show.title) { mutableIntStateOf(nextUp.seasonNumber ?: seasons.keys.first()) }
    LazyColumn(Modifier.navigationBarsPadding()) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Backdrop(show.backdropPath, show.posterPath, show.title)
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(show.title, style = MaterialTheme.typography.headlineMedium)
                    DotSeparatedRow(
                        listOfNotNull(
                            show.year?.toString(),
                            "${seasons.size} season${if (seasons.size == 1) "" else "s"}",
                            "${show.episodes.size} episodes"
                        )
                    )
                }
                PlayActions(
                    media = nextUp,
                    canControlTv = canControlTv,
                    onPlay = onPlay,
                    favourite = show.isFavorite,
                    onFavourite = { fav -> onFavourite(show.representative, fav) },
                    playLabel = buildString {
                        append(if (nextUp.isInProgress) "Resume " else "Play ")
                        append(nextUp.episodeLabel?.replace(" · ", "") ?: "")
                    }.trim()
                )
                show.overview?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                if (seasons.size > 1) {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(seasons.keys.toList()) { season ->
                            FilterChip(
                                selected = season == selectedSeason,
                                onClick = { selectedSeason = season },
                                label = { Text(if (season == 0) "Specials" else "Season $season") }
                            )
                        }
                    }
                }
            }
        }
        items(seasons[selectedSeason].orEmpty(), key = { it.id }) { episode ->
            EpisodeRow(episode, canControlTv, onPlay)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EpisodeRow(episode: CompanionMedia, canControlTv: Boolean, onPlay: (CompanionMedia, Boolean) -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium)) {
                Artwork(episode.stillPath ?: episode.backdropPath, size = "w300", modifier = Modifier.matchParentSize())
                if (episode.isInProgress) {
                    LinearWavyProgressIndicator(
                        progress = { episode.progressFraction },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp)
                    )
                }
            }
        },
        overlineContent = { episode.episodeLabel?.let { Text(it) } },
        headlineContent = {
            Text(episode.episodeTitle ?: "Episode ${episode.episodeNumber ?: ""}", maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            when {
                episode.completed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Watched")
                }
                episode.durationMs?.let(::formatRuntime) != null -> Text(formatRuntime(episode.durationMs!!)!!)
            }
        },
        trailingContent = {
            androidx.compose.material3.IconButton(onClick = { onPlay(episode, false) }, enabled = canControlTv) {
                Icon(Icons.Rounded.Cast, contentDescription = "Play on ${LocalDeviceNoun.current}")
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

fun formatRuntime(ms: Long): String? {
    val minutes = ms / 60_000
    if (minutes < 1) return null
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
