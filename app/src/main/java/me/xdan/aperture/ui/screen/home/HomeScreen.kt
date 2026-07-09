package me.xdan.aperture.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.ui.component.MediaCard
import me.xdan.aperture.ui.theme.HeroGradientEnd
import me.xdan.aperture.ui.theme.HeroGradientStart

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMediaClick: (Long) -> Unit
) {
    val state by viewModel.homeState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            HomeState.Loading -> {
                Text("Scanning library...", modifier = Modifier.align(Alignment.Center))
            }
            HomeState.Empty -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No media found on device.")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.softRefresh() }) {
                        Text("Rescan")
                    }
                }
            }
            is HomeState.Success -> {
                HomeContent(s, onMediaClick)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeState.Success,
    onMediaClick: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            FeaturedCarousel(state.featured, onMediaClick)
        }
        items(state.rows) { row ->
            HomeMediaRow(row, onMediaClick, state.progressMap)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FeaturedCarousel(
    featured: List<MediaEntity>,
    onMediaClick: (Long) -> Unit
) {
    if (featured.isEmpty()) return

    Carousel(
        itemCount = featured.size,
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(16.dp)
    ) { index ->
        val media = featured[index]
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(TmdbApi.IMAGE_BASE_URL + "original" + (media.backdropPath ?: ""))
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(HeroGradientStart, HeroGradientEnd),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .align(Alignment.BottomStart)
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onMediaClick(media.id) }) {
                    Text("Watch Now")
                }
            }
        }
    }
}

@Composable
private fun HomeMediaRow(
    row: HomeRow,
    onMediaClick: (Long) -> Unit,
    progressMap: Map<Long, Float> = emptyMap()
) {
    if (row.items.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(row.items) { media ->
                MediaCard(
                    media = media,
                    onClick = { onMediaClick(media.id) },
                    modifier = Modifier.width(150.dp),
                    progress = progressMap[media.id] ?: 0f
                )
            }
        }
    }
}
