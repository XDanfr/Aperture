package me.xdan.aperture.ui.screen.details

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.items
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
    viewModel: MediaDetailsViewModel,
    onPlay: (Long, Boolean) -> Unit,
    onClose: () -> Unit,
    restoreFocus: () -> Unit = {}
) {
    val media by viewModel.media.collectAsState()
    val playbackProgress by viewModel.progress.collectAsState()
    val assetCandidates by viewModel.assetCandidates.collectAsState()
    val isLoadingAssets by viewModel.isLoadingAssets.collectAsState()
    val playButtonFocusRequester = remember { FocusRequester() }
    var ignoreNextPlayFocus by remember { mutableStateOf(true) }
    var waitForLeftRelease by remember { mutableStateOf(false) }
    var restoreFocusAfterClose by remember { mutableStateOf(false) }
    var displayedMedia by remember { mutableStateOf<me.xdan.aperture.data.local.entity.MediaEntity?>(null) }
    var showAssetPicker by remember { mutableStateOf(false) }
    val isVisible = mediaId != null && displayedMedia?.id == mediaId
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
        mediaId?.let(viewModel::loadMedia)
    }

    LaunchedEffect(media, mediaId) {
        if (mediaId != null && media?.id == mediaId) {
            displayedMedia = media
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            restoreFocusAfterClose = false
            ignoreNextPlayFocus = true
            waitForLeftRelease = false
            playButtonFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(isVisible, restoreFocusAfterClose) {
        if (!isVisible && restoreFocusAfterClose) {
            delay(320)
            restoreFocus()
            restoreFocusAfterClose = false
        }
    }

    BackHandler(enabled = mediaId != null) {
        closeModal()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
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
                            if (m.backdropPath.isNullOrBlank()) {
                                ArtworkFallback(
                                    title = m.title,
                                    isFocused = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(TmdbApi.IMAGE_BASE_URL + "w780" + m.backdropPath)
                                        .crossfade(false)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = m.title,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = if (m.year != null) "${m.year}" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = m.overview ?: "No synopsis available.",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 6
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { onPlay(m.id, false) },
                                modifier = Modifier
                                    .focusRequester(playButtonFocusRequester)
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
                                        if (keyEvent.key != Key.DirectionLeft) {
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
                onDismiss = { showAssetPicker = false }
            )
        }
    }
}

@Composable
private fun AssetPickerDialog(
    candidates: List<me.xdan.aperture.data.remote.dto.TmdbResult>,
    loading: Boolean,
    onSelect: (me.xdan.aperture.data.remote.dto.TmdbResult) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(52.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(28.dp)) {
                Text("Choose artwork and metadata", style = MaterialTheme.typography.headlineMedium)
                Text("Select the correct TMDB match for this file.")
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
                        items(candidates, key = { it.id }) { candidate ->
                            Surface(
                                onClick = { onSelect(candidate) },
                                modifier = Modifier.aspectRatio(2f / 3f),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
                            ) {
                                if (candidate.posterPath.isNullOrBlank()) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(candidate.title ?: candidate.name ?: "Unknown", modifier = Modifier.padding(10.dp))
                                    }
                                } else {
                                    AsyncImage(
                                        model = TmdbApi.IMAGE_BASE_URL + "w342" + candidate.posterPath,
                                        contentDescription = candidate.title ?: candidate.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
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
