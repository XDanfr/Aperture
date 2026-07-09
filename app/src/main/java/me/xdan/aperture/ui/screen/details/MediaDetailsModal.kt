package me.xdan.aperture.ui.screen.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.ui.theme.GlassBackground

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaDetailsModal(
    mediaId: Long,
    viewModel: MediaDetailsViewModel,
    onPlay: (Long) -> Unit,
    onClose: () -> Unit
) {
    val media by viewModel.media.collectAsState()
    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(mediaId) {
        viewModel.loadMedia(mediaId)
    }

    LaunchedEffect(media) {
        if (media != null) {
            playButtonFocusRequester.requestFocus()
        }
    }

    BackHandler(enabled = media != null) {
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
    ) {
        AnimatedVisibility(
            visible = media != null,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            media?.let { m ->
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(500.dp),
                    colors = SurfaceDefaults.colors(containerColor = GlassBackground),
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(TmdbApi.IMAGE_BASE_URL + "w780" + m.backdropPath)
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Gray),
                            contentScale = ContentScale.Crop
                        )
                        
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
                                modifier = Modifier.focusRequester(playButtonFocusRequester)
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
