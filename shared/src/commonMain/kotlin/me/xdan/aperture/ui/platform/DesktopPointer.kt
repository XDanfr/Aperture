package me.xdan.aperture.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import me.xdan.aperture.platform.AppBuild

/*
 * Mouse behaviour for the desktop app. Android TV has no pointer, so every helper here returns the
 * modifier unchanged there and the TV app behaves exactly as before.
 */

/** True when a mouse is the main pointer (the desktop app). */
val usesMouse: Boolean get() = AppBuild.PLATFORM.isDesktop

/** Toggles the desktop window between fullscreen and windowed; null where there's no window. */
val LocalToggleFullscreen = staticCompositionLocalOf<(() -> Unit)?> { null }

/** A cursor that shows nothing, for hiding the pointer over playing video. */
expect fun hiddenPointerIcon(): PointerIcon

/**
 * A primary-button click that nothing inside handled: a dialog's scrim, the video behind the
 * player controls. Buttons and panels inside consume their clicks, so they don't trigger it.
 * [onDoubleClick], when given, replaces the second of two quick clicks.
 */
@Composable
fun Modifier.onBackgroundClick(
    enabled: Boolean = true,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    if (!usesMouse || !enabled) return this
    val click by rememberUpdatedState(onClick)
    val doubleClick by rememberUpdatedState(onDoubleClick)
    return pointerInput(Unit) {
        var lastClickAt = 0L
        awaitPointerEventScope {
            while (true) {
                val press = awaitPointerEvent()
                val down = press.changes.firstOrNull() ?: continue
                if (press.type != PointerEventType.Press || !press.buttons.isPrimaryPressed || down.isConsumed) continue
                var clicked = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    if (event.type == PointerEventType.Release) {
                        clicked = !change.isConsumed
                        break
                    }
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
                if (!clicked) continue
                val now = down.uptimeMillis
                val double = doubleClick
                if (double != null && now - lastClickAt < viewConfiguration.doubleTapTimeoutMillis) {
                    lastClickAt = 0L
                    double()
                } else {
                    lastClickAt = now
                    click()
                }
            }
        }
    }
}

/** Calls [onMove] whenever the mouse actually moves over this element (not for layout changes). */
@Composable
fun Modifier.onMouseMove(enabled: Boolean = true, onMove: () -> Unit): Modifier {
    if (!usesMouse || !enabled) return this
    val move by rememberUpdatedState(onMove)
    return pointerInput(Unit) {
        var last = Offset.Unspecified
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Move && event.type != PointerEventType.Enter) continue
                val position = event.changes.firstOrNull()?.position ?: continue
                if (position != last) {
                    last = position
                    move()
                }
            }
        }
    }
}

/** Calls [onEnter] and [onExit] as the mouse enters and leaves this element. */
@Composable
fun Modifier.onMouseEnterExit(enabled: Boolean = true, onEnter: () -> Unit, onExit: () -> Unit): Modifier {
    if (!usesMouse || !enabled) return this
    val enter by rememberUpdatedState(onEnter)
    val exit by rememberUpdatedState(onExit)
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                when (awaitPointerEvent().type) {
                    PointerEventType.Enter -> enter()
                    PointerEventType.Exit -> exit()
                    else -> Unit
                }
            }
        }
    }
}
