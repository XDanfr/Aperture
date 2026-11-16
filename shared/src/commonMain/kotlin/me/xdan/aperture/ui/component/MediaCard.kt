package me.xdan.aperture.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import me.xdan.aperture.ui.platform.rememberScreenWidthDp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.ui.theme.ApertureTheme

/** A TMDB path at [size], or a full URL (e.g. a Jellyfin image) as it is. */
fun artworkUrl(pathOrUrl: String?, size: String): String? = pathOrUrl
    ?.takeIf { it.isNotBlank() }
    ?.let { if (it.startsWith("http://") || it.startsWith("https://")) it else TmdbApi.IMAGE_BASE_URL + size + it }

@Composable
fun MediaCard(
    media: MediaEntity,
    onClick: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    aspectRatio: Float = 2f / 3f,
    preferEpisodeStill: Boolean = false,
    progress: Float = 0f,
    focusScale: Float = 1.08f,
    drawerFocusRequester: FocusRequester? = null,
    onFocused: (FocusRequester) -> Unit = {},
    onLongClick: ((FocusRequester, Boolean) -> Unit)? = null
) {
    val artworkPath = if (preferEpisodeStill && media.type == "EPISODE") {
        media.stillPath
    } else {
        media.posterPath
    }
    val fallbackTitle = if (preferEpisodeStill && media.type == "EPISODE") {
        media.episodeTitle ?: buildString {
            media.seasonNumber?.let { append("S$it") }
            media.episodeNumber?.let { append("E$it") }
        }.ifBlank { media.title }
    } else {
        media.title
    }
    ArtworkCard(
        artwork = artworkUrl(artworkPath, "w500"),
        title = fallbackTitle,
        onClick = onClick,
        modifier = modifier,
        focusRequester = focusRequester,
        aspectRatio = aspectRatio,
        progress = progress,
        focusScale = focusScale,
        drawerFocusRequester = drawerFocusRequester,
        onFocused = onFocused,
        onLongClick = onLongClick
    )
}

/** A Jellyfin (or any catalog) item as a poster, or as a 16:9 still when [landscape]. */
@Composable
fun CatalogCard(
    item: me.xdan.aperture.domain.catalog.CatalogItem,
    onClick: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    landscape: Boolean = false,
    focusScale: Float = 1.08f,
    drawerFocusRequester: FocusRequester? = null,
    onFocused: (FocusRequester) -> Unit = {},
    onLongClick: ((FocusRequester, Boolean) -> Unit)? = null
) {
    ArtworkCard(
        artwork = if (landscape) item.thumb ?: item.backdrop ?: item.poster else item.poster,
        title = if (landscape) listOfNotNull(item.title, item.episodeLabel).joinToString(" · ") else item.title,
        onClick = onClick,
        modifier = modifier,
        focusRequester = focusRequester,
        aspectRatio = if (landscape) 16f / 9f else 2f / 3f,
        progress = item.progress,
        focusScale = focusScale,
        drawerFocusRequester = drawerFocusRequester,
        onFocused = onFocused,
        onLongClick = onLongClick
    )
}

/** The focusable artwork tile behind every media card. [artwork] is a ready-to-load URL. */
@Composable
fun ArtworkCard(
    artwork: String?,
    title: String,
    onClick: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    aspectRatio: Float = 2f / 3f,
    progress: Float = 0f,
    focusScale: Float = 1.08f,
    drawerFocusRequester: FocusRequester? = null,
    onFocused: (FocusRequester) -> Unit = {},
    onLongClick: ((FocusRequester, Boolean) -> Unit)? = null
) {
    val fallbackTitle = title
    var isFocused by remember { mutableStateOf(false) }
    val internalFocusRequester = remember { FocusRequester() }
    val cardFocusRequester = focusRequester ?: internalFocusRequester
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var longClickTriggered by remember { mutableStateOf(false) }
    val screenWidthDp = rememberScreenWidthDp()
    val density = LocalDensity.current
    val screenWidthPx = with(density) { screenWidthDp.dp.toPx() }
    val opensToRight = remember { booleanArrayOf(true) }

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .onGloballyPositioned { coordinates ->
                opensToRight[0] = coordinates.boundsInWindow().center.x < screenWidthPx / 2f
            }
            .zIndex(if (isFocused) 1f else 0f)
            .focusGlow(isFocused)
            .graphicsLayer { clip = false }
    ) {
        Surface(
            onClick = { onClick(cardFocusRequester) },
            interactionSource = interactionSource,
            scale = ClickableSurfaceDefaults.scale(
                focusedScale = focusScale
            ),
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (onLongClick == null) return@onPreviewKeyEvent false
                    val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter
                    if (!isSelect) return@onPreviewKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (holdJob == null && !longClickTriggered) {
                                longClickTriggered = false
                                holdJob = scope.launch {
                                    delay(550)
                                    longClickTriggered = true
                                    onLongClick(cardFocusRequester, opensToRight[0])
                                }
                            }
                            longClickTriggered
                        }
                        KeyEventType.KeyUp -> {
                            holdJob?.cancel()
                            holdJob = null
                            val consume = longClickTriggered
                            longClickTriggered = false
                            consume
                        }
                        else -> false
                    }
                }
                .then(
                    if (drawerFocusRequester != null) {
                        Modifier.focusProperties { left = drawerFocusRequester }
                    } else {
                        Modifier
                    }
                )
                .focusRequester(cardFocusRequester)
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) onFocused(cardFocusRequester)
                }
                .graphicsLayer { clip = false },
            shape = ClickableSurfaceDefaults.shape(ApertureTheme.shapes.poster),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = ApertureTheme.colorScheme.mediaCardBackground,
                focusedContainerColor = ApertureTheme.colorScheme.focusedMediaCardBackground
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(2.dp, ApertureTheme.colorScheme.border),
                    shape = ApertureTheme.shapes.poster
                )
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (artwork.isNullOrBlank()) {
                    ArtworkFallback(
                        title = fallbackTitle,
                        isFocused = isFocused
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(artwork)
                            .crossfade(false)
                            .build(),
                        contentDescription = fallbackTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = ApertureTheme.spacing.small, vertical = ApertureTheme.spacing.small)
                            .height(if (isFocused) 8.dp else 6.dp)
                            .clip(ApertureTheme.shapes.button)
                            .background(Color.Black.copy(alpha = 0.82f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(ApertureTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}
