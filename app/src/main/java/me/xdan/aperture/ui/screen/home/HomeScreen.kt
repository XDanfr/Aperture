package me.xdan.aperture.ui.screen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.ui.component.MediaCard
import me.xdan.aperture.ui.component.ArtworkFallback
import me.xdan.aperture.ui.theme.HeroGradientEnd
import me.xdan.aperture.ui.theme.HeroGradientStart

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMediaClick: (Long, FocusRequester) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    restoreFocusKey: String?,
    onFocusKeyChanged: (String) -> Unit,
    onContentFocused: (FocusRequester) -> Unit,
    onActiveMediaChanged: (Long) -> Unit = {}
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
                HomeContent(
                    state = s,
                    onMediaClick = onMediaClick,
                    onMediaLongClick = onMediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    restoreFocusKey = restoreFocusKey,
                    onFocusKeyChanged = onFocusKeyChanged,
                    onContentFocused = onContentFocused,
                    onActiveMediaChanged = onActiveMediaChanged
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeState.Success,
    onMediaClick: (Long, FocusRequester) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    restoreFocusKey: String?,
    onFocusKeyChanged: (String) -> Unit,
    onContentFocused: (FocusRequester) -> Unit,
    onActiveMediaChanged: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val refreshAlpha = remember { Animatable(1f) }
    val resolvedRestoreFocusKey = restoreFocusKey.takeIf { key ->
        key == HOME_SPOTLIGHT_FOCUS_KEY || state.rows.any { row ->
            row.items.any { media -> key == "row:${row.title}:${media.id}" }
        }
    }

    LaunchedEffect(Unit) {
        val restoredRowTitle = resolvedRestoreFocusKey
            ?.takeIf { it.startsWith("row:") }
            ?.removePrefix("row:")
            ?.substringBeforeLast(":")
        val restoredRowIndex = state.rows.indexOfFirst { it.title == restoredRowTitle }
        if (restoredRowIndex >= 0) {
            listState.scrollToItem(restoredRowIndex + 1)
        }
    }

    LaunchedEffect(state.suggestionGeneration) {
        if (state.suggestionGeneration > 0) {
            refreshAlpha.snapTo(0.42f)
            listState.animateScrollToItem(0)
            onFocusKeyChanged(HOME_SPOTLIGHT_FOCUS_KEY)
            delay(80)
            runCatching { contentEntryFocusRequester.requestFocus() }
            refreshAlpha.animateTo(1f, tween(320))
        }
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = refreshAlpha.value },
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            FeaturedCarousel(
                featured = state.featured,
                progressMap = state.progressMap,
                completedMediaIds = state.completedMediaIds,
                continueMediaIds = state.continueMediaIds,
                onMediaClick = onMediaClick,
                drawerFocusRequester = drawerFocusRequester,
                contentEntryFocusRequester = contentEntryFocusRequester,
                isContentEntry = resolvedRestoreFocusKey == null || resolvedRestoreFocusKey == HOME_SPOTLIGHT_FOCUS_KEY,
                onFocusKeyChanged = onFocusKeyChanged,
                onContentFocused = onContentFocused,
                onActiveMediaChanged = onActiveMediaChanged
            )
        }
        items(state.rows) { row ->
            HomeMediaRow(
                row = row,
                onMediaClick = onMediaClick,
                onMediaLongClick = onMediaLongClick,
                progressMap = state.progressMap,
                drawerFocusRequester = drawerFocusRequester,
                contentEntryFocusRequester = contentEntryFocusRequester,
                restoreFocusKey = resolvedRestoreFocusKey,
                onFocusKeyChanged = onFocusKeyChanged,
                onContentFocused = onContentFocused,
                onActiveMediaChanged = onActiveMediaChanged
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FeaturedCarousel(
    featured: List<MediaEntity>,
    progressMap: Map<Long, Float>,
    completedMediaIds: Set<Long>,
    continueMediaIds: Set<Long>,
    onMediaClick: (Long, FocusRequester) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    isContentEntry: Boolean,
    onFocusKeyChanged: (String) -> Unit,
    onContentFocused: (FocusRequester) -> Unit,
    onActiveMediaChanged: (Long) -> Unit
) {
    if (featured.isEmpty()) return

    val carouselState = rememberCarouselState()
    val spotlightRequesters = remember(featured.map { it.id }) {
        List(featured.size) { FocusRequester() }
    }
    var focusActiveSpotlight by remember { mutableStateOf(false) }

    LaunchedEffect(carouselState.activeItemIndex, featured) {
        featured.getOrNull(carouselState.activeItemIndex)?.let { onActiveMediaChanged(it.id) }
    }

    LaunchedEffect(focusActiveSpotlight, carouselState.activeItemIndex) {
        if (focusActiveSpotlight) {
            delay(16)
            runCatching {
                if (isContentEntry) {
                    contentEntryFocusRequester.requestFocus()
                } else {
                    spotlightRequesters[carouselState.activeItemIndex].requestFocus()
                }
            }
        }
    }

    Carousel(
        itemCount = featured.size,
        carouselState = carouselState,
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(16.dp)
            .onFocusChanged { focusState ->
                focusActiveSpotlight = focusState.hasFocus
            }
    ) { index ->
        val media = featured[index]
        var isWatchNowFocused by remember(media.id) { mutableStateOf(false) }
        val watchNowFocusRequester = if (
            isContentEntry && index == carouselState.activeItemIndex
        ) {
            contentEntryFocusRequester
        } else {
            spotlightRequesters[index]
        }
        Box(modifier = Modifier.fillMaxSize()) {
            if (media.backdropPath.isNullOrBlank()) {
                ArtworkFallback(
                    title = media.title,
                    isFocused = isWatchNowFocused,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(TmdbApi.IMAGE_BASE_URL + "original" + media.backdropPath)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
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
                Button(
                    onClick = { onMediaClick(media.id, watchNowFocusRequester) },
                    modifier = Modifier
                        .then(
                            if (drawerFocusRequester != null) {
                                Modifier.focusProperties { left = drawerFocusRequester }
                            } else Modifier
                        )
                        .focusRequester(watchNowFocusRequester)
                        .onFocusChanged {
                            isWatchNowFocused = it.isFocused
                            if (it.isFocused) {
                                onFocusKeyChanged(HOME_SPOTLIGHT_FOCUS_KEY)
                                onContentFocused(watchNowFocusRequester)
                            }
                        }
                ) {
                    val progress = progressMap[media.id] ?: 0f
                    Text(
                        when {
                            media.id in continueMediaIds -> "Continue"
                            progress >= 0.05f && progress < 0.95f -> "Continue"
                            media.id in completedMediaIds -> "Rewatch"
                            else -> "Watch Now"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeMediaRow(
    row: HomeRow,
    onMediaClick: (Long, FocusRequester) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    progressMap: Map<Long, Float> = emptyMap(),
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    restoreFocusKey: String?,
    onFocusKeyChanged: (String) -> Unit,
    onContentFocused: (FocusRequester) -> Unit,
    onActiveMediaChanged: (Long) -> Unit
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
            modifier = Modifier.height(250.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(row.items) { index, media ->
                val focusKey = "row:${row.title}:${media.id}"
                MediaCard(
                    media = media,
                    onClick = { requester -> onMediaClick(media.id, requester) },
                    modifier = Modifier.width(150.dp),
                    focusRequester = contentEntryFocusRequester.takeIf {
                        restoreFocusKey == focusKey
                    },
                    progress = progressMap[media.id] ?: 0f,
                    drawerFocusRequester = drawerFocusRequester.takeIf { index == 0 },
                    onFocused = { requester ->
                        onFocusKeyChanged(focusKey)
                        onContentFocused(requester)
                        onActiveMediaChanged(media.id)
                    },
                    onLongClick = { requester, opensToRight ->
                        onMediaLongClick(media, requester, row.title == "Continue Watching", opensToRight)
                    }
                )
            }
        }
    }
}

private const val HOME_SPOTLIGHT_FOCUS_KEY = "spotlight"
