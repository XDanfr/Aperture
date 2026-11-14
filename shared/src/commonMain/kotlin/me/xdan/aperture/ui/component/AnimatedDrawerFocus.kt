package me.xdan.aperture.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import me.xdan.aperture.ui.theme.ApertureTheme
import kotlin.math.max
import kotlin.math.min

@Stable
class DrawerFocusMotionState {
    internal var coordinates: LayoutCoordinates? = null
    internal val itemBounds = mutableStateMapOf<String, Rect>()
    internal var focusedKey by mutableStateOf<String?>(null)
    internal val highlightTop = Animatable(0f)
    internal var highlightReady by mutableStateOf(false)

    fun item(key: String): Modifier = Modifier
        .onGloballyPositioned { itemCoordinates ->
            val container = coordinates
            if (container?.isAttached == true && itemCoordinates.isAttached) {
                itemBounds[key] = container.localBoundingBoxOf(itemCoordinates, clipBounds = false)
            }
        }
        .onFocusChanged { if (it.isFocused) focusedKey = key }

    internal fun coverage(key: String): Float {
        if (!highlightReady) return 0f
        val item = itemBounds[key] ?: return 0f
        val highlightHeight = focusedKey?.let { itemBounds[it]?.height } ?: return 0f
        if (item.height <= 0f) return 0f
        val top = highlightTop.value
        val overlap = min(item.bottom, top + highlightHeight) - max(item.top, top)
        return (overlap / item.height).coerceIn(0f, 1f)
    }
}

/** One moving pill behind stationary sidebar items; no animated remeasurement. */
@Composable
fun AnimatedDrawerFocus(
    open: Boolean,
    entryKey: String?,
    content: @Composable (DrawerFocusMotionState) -> Unit
) {
    val state = remember { DrawerFocusMotionState() }
    val targetKey = if (open) state.focusedKey ?: entryKey else entryKey
    val bounds = targetKey?.let { state.itemBounds[it] }
    var previousKey by remember { mutableStateOf<String?>(null) }
    val motion = ApertureTheme.motion
    val density = LocalDensity.current

    LaunchedEffect(open, targetKey, bounds?.top) {
        if (!open) {
            state.highlightReady = false
            previousKey = null
            bounds?.let { state.highlightTop.snapTo(it.top) }
            return@LaunchedEffect
        }
        val target = bounds ?: return@LaunchedEffect
        val movingBetweenItems = previousKey != null && previousKey != targetKey
        if (previousKey == null) {
            // Allow the drawer's opening layout to settle before showing the
            // pill. This also prevents replaying the last hover on reopen.
            state.highlightReady = false
            withFrameNanos { }
            withFrameNanos { }
            val placedTarget = targetKey?.let { state.itemBounds[it] } ?: target
            state.highlightTop.snapTo(placedTarget.top)
            previousKey = targetKey
            state.highlightReady = true
        } else if (movingBetweenItems) {
            previousKey = targetKey
            state.highlightTop.animateTo(target.top, motion.focus())
        } else {
            // Geometry changes within the same item are not vertical navigation.
            state.highlightTop.snapTo(target.top)
        }
    }

    Box(Modifier.onGloballyPositioned { state.coordinates = it }) {
        if (bounds != null) {
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = bounds.left
                        translationY = state.highlightTop.value
                        alpha = if (open && state.highlightReady) 1f else 0f
                    }
                    .width(with(density) { bounds.width.toDp() })
                    .height(with(density) { bounds.height.toDp() })
                    .background(MaterialTheme.colorScheme.inverseSurface, ApertureTheme.shapes.button)
            )
        }
        content(state)
    }
}

/** Tint follows the same pill position, without a separate animation per label. */
@Composable
fun DrawerFocusForeground(
    state: DrawerFocusMotionState,
    key: String,
    open: Boolean,
    content: @Composable () -> Unit
) {
    if (!open) {
        content()
        return
    }
    val normal = MaterialTheme.colorScheme.onSurface
    val highlighted = MaterialTheme.colorScheme.inverseOnSurface
    val color by remember(state, key, normal, highlighted) {
        derivedStateOf(structuralEqualityPolicy()) { lerp(normal, highlighted, state.coverage(key)) }
    }
    CompositionLocalProvider(LocalContentColor provides color, content = content)
}
