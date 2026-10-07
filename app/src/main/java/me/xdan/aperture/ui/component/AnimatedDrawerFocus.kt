package me.xdan.aperture.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.tv.material3.MaterialTheme
import me.xdan.aperture.ui.theme.ApertureTheme

@Stable
class DrawerFocusMotionState {
    internal var coordinates: LayoutCoordinates? = null
    internal val itemBounds = mutableStateMapOf<String, Rect>()
    internal var focusedKey by mutableStateOf<String?>(null)

    fun item(key: String): Modifier = Modifier
        .onGloballyPositioned { itemCoordinates ->
            val container = coordinates
            if (container?.isAttached == true && itemCoordinates.isAttached) {
                itemBounds[key] = container.localBoundingBoxOf(itemCoordinates, clipBounds = false)
            }
        }
        .onFocusChanged { if (it.isFocused) focusedKey = key }
}

/** One moving pill behind stationary sidebar items; no animated remeasurement. */
@Composable
fun AnimatedDrawerFocus(
    open: Boolean,
    content: @Composable (DrawerFocusMotionState) -> Unit
) {
    val state = remember { DrawerFocusMotionState() }
    val bounds = state.focusedKey?.let { state.itemBounds[it] }
    val density = LocalDensity.current
    Box(Modifier.onGloballyPositioned { state.coordinates = it }) {
        if (bounds != null) {
            val top by animateFloatAsState(bounds.top, ApertureTheme.motion.focus(), label = "drawerFocusTop")
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = bounds.left
                        translationY = top
                        alpha = if (open) 1f else 0f
                    }
                    .width(with(density) { bounds.width.toDp() })
                    .height(with(density) { bounds.height.toDp() })
                    .background(MaterialTheme.colorScheme.inverseSurface, ApertureTheme.shapes.button)
            )
        }
        content(state)
    }
}
