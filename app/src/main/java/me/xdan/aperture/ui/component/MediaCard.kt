package me.xdan.aperture.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.ui.theme.ApertureTheme
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
    focusScale: Float = 1.1f,
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
    val opensToRight = remember { booleanArrayOf(true) }

    val animatedScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isFocused -> focusScale
            else -> 1f
        },
        animationSpec = ApertureTheme.motion.focus(),
        label = "animatedScale"
    )

    val animatedElevation by animateDpAsState(
        targetValue = if (isFocused) ApertureTheme.elevation.focusedCard else 0.dp,
        animationSpec = ApertureTheme.motion.focus(),
        label = "animatedElevation"
    )

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 0.dp,
        animationSpec = ApertureTheme.motion.focus(),
        label = "animatedBorderWidth"
    )

    val glowColor = ApertureTheme.colorScheme.primary
    val borderColor = ApertureTheme.colorScheme.border

    val animatedGlowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.6f else 0f,
        animationSpec = ApertureTheme.motion.focus(),
        label = "animatedGlowAlpha"
    )

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .onGloballyPositioned { coordinates ->
                opensToRight[0] = coordinates.boundsInWindow().center.x < screenWidthPx / 2f
            }
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer { clip = false }
    ) {
        Surface(
            onClick = { onClick(cardFocusRequester) },
            interactionSource = interactionSource,
            scale = ClickableSurfaceDefaults.scale(
                focusedScale = 1f,
                pressedScale = 1f
            ),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false }
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
                },
            shape = ClickableSurfaceDefaults.shape(RectangleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = androidx.tv.material3.Border.None
            ),
            glow = ClickableSurfaceDefaults.glow(
                glow = Glow.None,
                focusedGlow = Glow.None,
                pressedGlow = Glow.None
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp) // Provide buffer for zoom
                    .scale(animatedScale)
                    .drawBehind {
                        if (animatedGlowAlpha > 0f) {
                            val shadowColor = glowColor.copy(alpha = animatedGlowAlpha).toArgb()
                            val transparentColor = glowColor.copy(alpha = 0f).toArgb()
                            
                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = transparentColor
                                    setShadowLayer(
                                        20.dp.toPx(),
                                        0f,
                                        0f,
                                        shadowColor
                                    )
                                }
                                val rect = android.graphics.RectF(0f, 0f, size.width, size.height)
                                val radius = 8.dp.toPx()
                                drawRoundRect(rect, radius, radius, paint)
                            }
                        }
                    }
                    .border(
                        width = animatedBorderWidth,
                        color = borderColor,
                        shape = ApertureTheme.shapes.poster
                    )
                    .clip(ApertureTheme.shapes.poster)
                    .background(ApertureTheme.colorScheme.mediaCardBackground)
            ) {
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
                            .padding(horizontal = ApertureTheme.spacing.small, vertical = ApertureTheme.spacing.small)
                            .height(if (isFocused) 8.dp else 6.dp)
                            .clip(ApertureTheme.shapes.button)
                            .background(Color.Black.copy(alpha = 0.82f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(
                                    if (isFocused) {
                                        ApertureTheme.colorScheme.onPrimary
                                    } else {
                                        ApertureTheme.colorScheme.primary
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}
