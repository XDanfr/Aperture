package me.xdan.aperture.ui.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import me.xdan.aperture.ui.platform.usesMouse

/**
 * Desktop mouse scrubbing for the seek bars, as in a web video player: press anywhere on the bar
 * to jump there and drag to scrub (the same scrubbing state and preview the D-pad uses), release
 * to play from there. Hovering reports the position under the pointer, for a preview without
 * scrubbing. Positions are fractions of the bar's width. Nothing changes on Android TV.
 */
@Composable
fun Modifier.seekBarPointerInput(
    onHover: (Float?) -> Unit,
    onScrubStart: (Float) -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit
): Modifier {
    if (!usesMouse) return this
    val hover by rememberUpdatedState(onHover)
    val start by rememberUpdatedState(onScrubStart)
    val scrub by rememberUpdatedState(onScrub)
    val end by rememberUpdatedState(onScrubEnd)
    return pointerHoverIcon(PointerIcon.Hand).pointerInput(Unit) {
        awaitPointerEventScope {
            var dragging = false
            while (true) {
                // The Initial pass sees the press before the bar's children: the hover preview
                // card overlaps the top of the bar and would otherwise take the click.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                val fraction = if (size.width > 0) (change.position.x / size.width).coerceIn(0f, 1f) else 0f
                when (event.type) {
                    PointerEventType.Press -> if (event.buttons.isPrimaryPressed) {
                        dragging = true
                        hover(null)
                        start(fraction)
                        change.consume()
                    }
                    PointerEventType.Move, PointerEventType.Enter -> if (dragging) {
                        scrub(fraction)
                        change.consume()
                    } else {
                        hover(fraction)
                    }
                    PointerEventType.Release -> if (dragging) {
                        dragging = false
                        end()
                        change.consume()
                    }
                    // Dragging off either end of the bar scrubs to that end.
                    PointerEventType.Exit -> if (dragging) scrub(fraction) else hover(null)
                    else -> Unit
                }
            }
        }
    }
}
