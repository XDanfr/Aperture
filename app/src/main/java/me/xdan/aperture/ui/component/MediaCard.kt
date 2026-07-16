package me.xdan.aperture.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.remote.api.TmdbApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MediaCard(
    media: MediaEntity,
    onClick: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    aspectRatio: Float = 2f / 3f,
    preferEpisodeStill: Boolean = false,
    progress: Float = 0f,
    focusScale: Float = 1.05f,
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
    var isFocused by remember { mutableStateOf(false) }
    val internalFocusRequester = remember { FocusRequester() }
    val cardFocusRequester = focusRequester ?: internalFocusRequester
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var longClickTriggered by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    // This value is only read when the long-press action fires. Keeping it as
    // Compose state caused every newly positioned/off-screen card to recompose
    // on its first layout pass, which made nested Home rows visibly wobble.
    val opensToRight = remember { booleanArrayOf(true) }

    val animatedScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isFocused -> focusScale
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "animatedScale"
    )
    val focusGlow = rememberFocusGlow(isFocused)

    Surface(
        onClick = { onClick(cardFocusRequester) },
        interactionSource = interactionSource,
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1f, // Handled by modifier below
            pressedScale = 1f
        ),
        modifier = modifier
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
            .onGloballyPositioned { coordinates ->
                opensToRight[0] = coordinates.boundsInWindow().center.x < screenWidthPx / 2f
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
            .scale(animatedScale)
            .aspectRatio(aspectRatio)
            .padding(4.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(8.dp)
            )
        ),
        glow = ClickableSurfaceDefaults.glow(
            glow = focusGlow,
            focusedGlow = focusGlow,
            pressedGlow = focusGlow
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (artworkPath.isNullOrBlank()) {
                ArtworkFallback(
                    title = fallbackTitle,
                    isFocused = isFocused
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(TmdbApi.IMAGE_BASE_URL + "w500" + artworkPath)
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
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .height(if (isFocused) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.Black.copy(alpha = 0.82f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(
                                if (isFocused) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                    )
                }
            }
        }
    }
}
