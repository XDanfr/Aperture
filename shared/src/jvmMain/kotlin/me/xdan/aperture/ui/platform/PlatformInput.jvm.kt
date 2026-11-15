package me.xdan.aperture.ui.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Desktop stand-in for Android's OnBackPressedDispatcher: the most recently registered enabled
 * callback handles back, and [fallback] (the root back stack) runs when none does.
 */
class DesktopBackDispatcher(var fallback: () -> Unit = {}) {
    internal class Callback(var enabled: Boolean, var onBack: () -> Unit)

    private val callbacks = mutableListOf<Callback>()

    internal fun add(callback: Callback) { callbacks += callback }
    internal fun remove(callback: Callback) { callbacks -= callback }

    fun dispatch() {
        callbacks.lastOrNull { it.enabled }?.onBack?.invoke() ?: fallback()
    }
}

val LocalDesktopBackDispatcher = staticCompositionLocalOf { DesktopBackDispatcher() }

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    val dispatcher = LocalDesktopBackDispatcher.current
    val currentOnBack by rememberUpdatedState(onBack)
    val callback = remember { DesktopBackDispatcher.Callback(enabled) { currentOnBack() } }
    SideEffect { callback.enabled = enabled }
    DisposableEffect(dispatcher, callback) {
        dispatcher.add(callback)
        onDispose { dispatcher.remove(callback) }
    }
}

@Composable
actual fun rememberBackDispatcher(): (() -> Unit)? {
    val dispatcher = LocalDesktopBackDispatcher.current
    return remember(dispatcher) { dispatcher::dispatch }
}

actual fun KeyEvent.isBackKey(): Boolean = key == Key.Escape || key == Key.Back

actual val KeyEvent.repeatCountCompat: Int get() = DesktopKeyRepeat.repeatCount(this)

actual val KeyEvent.isCanceledCompat: Boolean get() = false

/**
 * AWT reports auto-repeat as extra key-down events with no count. The desktop window feeds every
 * key event through [track] first, so later handlers can ask how often a key has repeated.
 */
object DesktopKeyRepeat {
    private val downCounts = mutableMapOf<Key, Int>()
    private val lastCounts = mutableMapOf<Key, Int>()

    fun track(event: KeyEvent) {
        when (event.type) {
            androidx.compose.ui.input.key.KeyEventType.KeyDown -> {
                val count = downCounts[event.key]?.plus(1) ?: 0
                downCounts[event.key] = count
                lastCounts[event.key] = count
            }
            androidx.compose.ui.input.key.KeyEventType.KeyUp -> {
                downCounts.remove(event.key)
                lastCounts[event.key] = 0
            }
        }
    }

    fun repeatCount(event: KeyEvent): Int = lastCounts[event.key] ?: 0
}

/** mpv inhibits the screensaver itself while video plays (`stop-screensaver`). */
@Composable
actual fun KeepScreenOn(enabled: Boolean) = Unit

@Composable
actual fun rememberScreenWidthDp(): Int {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val width = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
    return with(density) { width.toDp().value.toInt() }
}

actual fun edgeToEdgeDialogProperties() = androidx.compose.ui.window.DialogProperties(
    usePlatformDefaultWidth = false,
    dismissOnClickOutside = false
)

@Composable
actual fun Dialog(
    onDismissRequest: () -> Unit,
    properties: androidx.compose.ui.window.DialogProperties,
    content: @Composable () -> Unit
) = androidx.compose.ui.window.Dialog(onDismissRequest, properties) {
    // Registered before the content's handlers, so those win, as on Android.
    BackHandler(enabled = properties.dismissOnBackPress, onBack = onDismissRequest)
    if (properties.usePlatformDefaultWidth || !properties.dismissOnClickOutside) {
        // Compose closes default-width dialogs on outside clicks itself.
        content()
    } else {
        // Full-window dialogs draw their own scrim, so Compose never sees a click "outside". A
        // click that no panel took (panels are Surfaces, which absorb clicks) landed on the scrim.
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.fillMaxSize().onBackgroundClick(onClick = onDismissRequest)
        ) { content() }
    }
}

