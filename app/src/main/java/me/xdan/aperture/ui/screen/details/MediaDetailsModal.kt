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
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.ui.component.ArtworkFallback
import me.xdan.aperture.ui.theme.GlassBackground

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaDetailsModal(
    mediaId: Long?,
    viewModel: MediaDetailsViewModel,
    onPlay: (Long) -> Unit,
    onClose: () -> Unit,
    restoreFocus: () -> Unit = {}
) {
    val media by viewModel.media.collectAsState()
    val playButtonFocusRequester = remember { FocusRequester() }
    var ignoreNextPlayFocus by remember { mutableStateOf(true) }
    var waitForLeftRelease by remember { mutableStateOf(false) }
    var displayedMedia by remember { mutableStateOf<me.xdan.aperture.data.local.entity.MediaEntity?>(null) }
    val isVisible = mediaId != null && displayedMedia?.id == mediaId
    val closeModal = {
        restoreFocus()
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
            ignoreNextPlayFocus = true
            waitForLeftRelease = false
            playButtonFocusRequester.requestFocus()
        }
    }

    BackHandler(enabled = isVisible) {
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
                                onClick = { onPlay(m.id) },
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
                                Icon(Icons.Rounded.PlayArrow, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Play")
                            }
                            
                            OutlinedButton(
                                onClick = { /* TODO */ }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null)
                                Spacer(Modifier.width(8.dp))
                                Text("My List")
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}
