package me.xdan.aperture.ui.screen.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.ui.component.ArtworkFallback
import me.xdan.aperture.ui.theme.GlassBackground

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaDetailsModal(
    mediaId: Long?,
    episodeOnly: Boolean = false,
    viewModel: MediaDetailsViewModel,
    onPlay: (Long, Boolean) -> Unit,
    onClose: () -> Unit,
    restoreFocus: () -> Unit = {}
) {
    val media by viewModel.media.collectAsState()
    val playbackProgress by viewModel.progress.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val assetCandidates by viewModel.assetCandidates.collectAsState()
    val isLoadingAssets by viewModel.isLoadingAssets.collectAsState()
    val playButtonFocusRequester = remember { FocusRequester() }
    val episodeSelectorFocusRequester = remember { FocusRequester() }
    var ignoreNextPlayFocus by remember { mutableStateOf(true) }
    var waitForLeftRelease by remember { mutableStateOf(false) }
    var restoreFocusAfterClose by remember { mutableStateOf(false) }
    var displayedMedia by remember { mutableStateOf<me.xdan.aperture.data.local.entity.MediaEntity?>(null) }
    var showAssetPicker by remember { mutableStateOf(false) }
    val isVisible = mediaId != null && displayedMedia != null
    val hasActiveProgress = playbackProgress?.let { progress ->
        progress.duration > 0 &&
            progress.position >= progress.duration * 0.05 &&
            progress.position < progress.duration * 0.95
    } == true
    val hasBeenCompleted = playbackProgress?.let { progress ->
        progress.isCompleted ||
            (progress.duration > 0 && progress.position >= progress.duration * 0.95)
    } == true
    val closeModal = {
        restoreFocusAfterClose = true
        onClose()
    }

    LaunchedEffect(mediaId) {
        if (mediaId != null) {
            displayedMedia = null
            viewModel.loadMedia(mediaId)
        } else if (displayedMedia != null) {
            delay(320)
            displayedMedia = null
        }
    }

    LaunchedEffect(media, mediaId) {
        if (mediaId != null && (media?.id == mediaId || episodes.any { it.id == media?.id })) {
            displayedMedia = media
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            restoreFocusAfterClose = false
            ignoreNextPlayFocus = true
            waitForLeftRelease = false
            delay(48)
            playButtonFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(isVisible, restoreFocusAfterClose) {
        if (!isVisible && restoreFocusAfterClose) {
            delay(380)
            restoreFocus()
            restoreFocusAfterClose = false
        }
    }

    if (mediaId != null || displayedMedia != null) {
        Dialog(
            onDismissRequest = { if (mediaId != null) closeModal() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
            )
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 300)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                displayedMedia?.let { m ->
                    val showEpisodeSelector = episodes.isNotEmpty() && !episodeOnly
                    val artworkPath = if (episodeOnly && !m.stillPath.isNullOrBlank()) {
                        m.stillPath
                    } else {
                        m.backdropPath
                    }
                    val heading = if (episodeOnly) {
                        m.episodeTitle ?: "Episode ${m.episodeNumber ?: "?"}"
                    } else {
                        m.title
                    }
                    val subheading = if (episodeOnly) {
                        buildString {
                            append(m.title)
                            m.seasonNumber?.let { append(" · Season $it") }
                            m.episodeNumber?.let { append(" Episode $it") }
                        }
                    } else {
                        m.year?.toString().orEmpty()
                    }
                    val artworkHeight = if (showEpisodeSelector) 120.dp else 160.dp
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(500.dp)
                            .focusProperties {
                                canFocus = true
                            },
                        colors = SurfaceDefaults.colors(containerColor = GlassBackground),
                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                        ) {
                            if (artworkPath.isNullOrBlank()) {
                                ArtworkFallback(
                                    title = heading,
                                    isFocused = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(artworkHeight)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(TmdbApi.IMAGE_BASE_URL + "w780" + artworkPath)
                                        .crossfade(false)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(artworkHeight)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Text(
                            text = heading,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = subheading,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = m.episodeOverview ?: m.overview ?: "No synopsis available.",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (showEpisodeSelector) 2 else 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (showEpisodeSelector) {
                            Spacer(Modifier.height(10.dp))
                            EpisodeSelector(
                                episodes = episodes,
                                selectedEpisodeId = m.id,
                                entryFocusRequester = episodeSelectorFocusRequester,
                                playButtonFocusRequester = playButtonFocusRequester,
                                onSelectEpisode = viewModel::selectEpisode
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Row(
                            modifier = Modifier.heightIn(min = 52.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onPlay(m.id, false) },
                                modifier = Modifier
                                    .focusRequester(playButtonFocusRequester)
                                    .focusProperties {
                                        if (showEpisodeSelector) {
                                            up = episodeSelectorFocusRequester
                                        }
                                    }
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            if (ignoreNextPlayFocus) {
                                                ignoreNextPlayFocus = false
                                            } else {
                                                waitForLeftRelease = true
                                            }
                                        }
                                    }
                                    .onKeyEvent { keyEvent ->
                                        if (showEpisodeSelector && keyEvent.key == Key.DirectionUp) {
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                // Nested lazy lists are not always considered by TV
                                                // directional search. Move to the current season's
                                                // first/selected episode directly instead.
                                                episodeSelectorFocusRequester.requestFocus()
                                            }
                                            true
                                        } else if (keyEvent.key != Key.DirectionLeft) {
                                            false
                                        } else if (waitForLeftRelease) {
                                            if (keyEvent.type == KeyEventType.KeyUp) {
                                                waitForLeftRelease = false
                                            }
                                            true
                                        } else when (keyEvent.type) {
                                            KeyEventType.KeyDown -> true
                                            KeyEventType.KeyUp -> {
                                                closeModal()
                                                true
                                            }
                                            else -> false
                                        }
                                    }
                            ) {
                                Icon(
                                    if (hasBeenCompleted && !hasActiveProgress) {
                                        Icons.Rounded.Replay
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    },
                                    null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when {
                                        hasActiveProgress -> "Continue"
                                        hasBeenCompleted -> "Rewatch"
                                        else -> "Play"
                                    }
                                )
                            }

                            if (hasActiveProgress) {
                                IconButton(onClick = { onPlay(m.id, true) }) {
                                    Icon(Icons.Rounded.Replay, contentDescription = "Restart")
                                }
                            }
                            
                            OutlinedButton(
                                onClick = { viewModel.toggleFavorite(m.id) }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (m.isFavorite) "Remove from My List" else "My List")
                            }
                            IconButton(
                                onClick = {
                                    showAssetPicker = true
                                    viewModel.findAssetCandidates()
                                }
                            ) {
                                Icon(Icons.Rounded.ImageSearch, contentDescription = "Select artwork")
                            }
                        }
                        }
                    }
                }
            }
        }
        if (showAssetPicker) {
            AssetPickerDialog(
                candidates = assetCandidates,
                loading = isLoadingAssets,
                onSelect = {
                    viewModel.selectAssetCandidate(it)
                    showAssetPicker = false
                },
                onQueryChange = viewModel::findAssetCandidates,
                onDismiss = { showAssetPicker = false }
            )
        }
            }
        }
    }
}

@Composable
private fun EpisodeSelector(
    episodes: List<me.xdan.aperture.data.local.entity.MediaEntity>,
    selectedEpisodeId: Long,
    entryFocusRequester: FocusRequester,
    playButtonFocusRequester: FocusRequester,
    onSelectEpisode: (Long) -> Unit
) {
    val seasons = remember(episodes) {
        episodes.mapNotNull { it.seasonNumber }.distinct().sorted()
    }
    val selectedEpisode = episodes.firstOrNull { it.id == selectedEpisodeId }
    var selectedSeason by remember(episodes) {
        mutableStateOf(selectedEpisode?.seasonNumber ?: seasons.firstOrNull() ?: 0)
    }
    LaunchedEffect(selectedEpisode?.seasonNumber) {
        selectedEpisode?.seasonNumber?.let { selectedSeason = it }
    }
    Column(Modifier.fillMaxWidth()) {
        Text("Episodes", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lazyItems(seasons, key = { it }) { season ->
                Surface(
                    onClick = { selectedSeason = season },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (season == selectedSeason) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text("Season $season", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val seasonEpisodes = episodes.filter { (it.seasonNumber ?: 0) == selectedSeason }
            val entryEpisodeId = seasonEpisodes.firstOrNull { it.id == selectedEpisodeId }?.id
                ?: seasonEpisodes.firstOrNull()?.id
            lazyItems(
                seasonEpisodes,
                key = { it.id }
            ) { episode ->
                Surface(
                    onClick = { onSelectEpisode(episode.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        // Explicitly connect the button row to the currently selected
                        // episode. Nested lazy lists otherwise hand DPAD Up to the
                        // background dialog on some TV devices.
                        .focusRequester(
                            entryFocusRequester.takeIf { episode.id == entryEpisodeId }
                                ?: FocusRequester.Default
                        )
                        .focusProperties {
                            if (episode == seasonEpisodes.lastOrNull()) {
                                down = playButtonFocusRequester
                            }
                        },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (episode.id == selectedEpisodeId) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!episode.stillPath.isNullOrBlank()) {
                            AsyncImage(
                                model = TmdbApi.IMAGE_BASE_URL + "w300" + episode.stillPath,
                                contentDescription = null,
                                modifier = Modifier.width(92.dp).height(52.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column {
                            Text(
                                "Episode ${episode.episodeNumber ?: "?"}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                episode.episodeTitle ?: episode.filePath.substringAfterLast('/').substringBeforeLast('.'),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetPickerDialog(
    candidates: List<me.xdan.aperture.data.remote.dto.TmdbResult>,
    loading: Boolean,
    onSelect: (me.xdan.aperture.data.remote.dto.TmdbResult) -> Unit,
    onQueryChange: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        delay(400)
        onQueryChange(query)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(52.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(28.dp)) {
                Text("Choose artwork and metadata", style = MaterialTheme.typography.headlineMedium)
                Text("Correct the title, then select the matching TMDB result.")
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { androidx.compose.material3.Text("Search title") }
                )
                Spacer(Modifier.height(20.dp))
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                    candidates.isEmpty() -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("No matching artwork found.")
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        gridItems(candidates, key = { it.id }) { candidate ->
                            Surface(
                                onClick = { onSelect(candidate) },
                                modifier = Modifier.aspectRatio(2f / 3f),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    if (candidate.posterPath.isNullOrBlank()) {
                                        ArtworkFallback(
                                            title = candidate.title ?: candidate.name ?: "Unknown",
                                            isFocused = false,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        AsyncImage(
                                            model = TmdbApi.IMAGE_BASE_URL + "w342" + candidate.posterPath,
                                            contentDescription = candidate.title ?: candidate.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Text(
                                        candidate.title ?: candidate.name ?: "Unknown",
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.78f))
                                            .padding(8.dp),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 2
                                    )
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
