package me.xdan.aperture.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Pointer seeking supplements, rather than synthesizes, remote key events. */
@Composable
fun Modifier.pointerSeek(
    enabled: Boolean,
    onSeekingChanged: (Boolean) -> Unit,
    onSeek: (Float) -> Unit
): Modifier {
    val currentSeeking = rememberUpdatedState(onSeekingChanged)
    val currentSeek = rememberUpdatedState(onSeek)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            currentSeeking.value(true)
            try {
                if (size.width > 0) currentSeek.value((down.position.x / size.width).coerceIn(0f, 1f))
                do {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (size.width > 0) currentSeek.value((change.position.x / size.width).coerceIn(0f, 1f))
                    change.consume()
                } while (change.pressed)
            } finally {
                currentSeeking.value(false)
            }
        }
    }
}
