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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.remote.api.TmdbApi

@Composable
fun MediaCard(
    media: MediaEntity,
    onClick: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    aspectRatio: Float = 2f / 3f,
    progress: Float = 0f,
    drawerFocusRequester: FocusRequester? = null,
    onFocused: (FocusRequester) -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val internalFocusRequester = remember { FocusRequester() }
    val cardFocusRequester = focusRequester ?: internalFocusRequester
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isFocused -> 1.05f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "animatedScale"
    )

    Surface(
        onClick = { onClick(cardFocusRequester) },
        interactionSource = interactionSource,
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1f, // Handled by modifier below
            pressedScale = 1f
        ),
        modifier = modifier
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
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (media.posterPath.isNullOrBlank()) {
                ArtworkFallback(
                    title = media.title,
                    isFocused = isFocused
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(TmdbApi.IMAGE_BASE_URL + "w500" + media.posterPath)
                        .crossfade(false)
                        .build(),
                    contentDescription = media.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}
