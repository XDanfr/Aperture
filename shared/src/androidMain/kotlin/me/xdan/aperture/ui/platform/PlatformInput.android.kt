package me.xdan.aperture.ui.platform

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) =
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)

@Composable
actual fun rememberBackDispatcher(): (() -> Unit)? {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher ?: return null
    return dispatcher::onBackPressed
}

actual fun KeyEvent.isBackKey(): Boolean = key == Key.Back

actual val KeyEvent.repeatCountCompat: Int get() = nativeKeyEvent.repeatCount

actual val KeyEvent.isCanceledCompat: Boolean get() = nativeKeyEvent.isCanceled

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val hostView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(hostView, enabled) {
        hostView.keepScreenOn = enabled
        onDispose { hostView.keepScreenOn = false }
    }
}

@Composable
actual fun rememberScreenWidthDp(): Int = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp

actual fun edgeToEdgeDialogProperties() = androidx.compose.ui.window.DialogProperties(
    usePlatformDefaultWidth = false,
    dismissOnClickOutside = false,
    decorFitsSystemWindows = false
)

// Android dialogs already route Back through their own dispatcher.
@Composable
actual fun Dialog(
    onDismissRequest: () -> Unit,
    properties: androidx.compose.ui.window.DialogProperties,
    content: @Composable () -> Unit
) = androidx.compose.ui.window.Dialog(onDismissRequest, properties, content)
