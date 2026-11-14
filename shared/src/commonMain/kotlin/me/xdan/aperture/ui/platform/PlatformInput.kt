package me.xdan.aperture.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent

/**
 * Back navigation. Android uses the activity's OnBackPressedDispatcher, exactly as before; desktop
 * has its own dispatcher fed by Escape and the mouse back button.
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** Triggers one back step through the platform dispatcher, or null if there is none. */
@Composable
expect fun rememberBackDispatcher(): (() -> Unit)?

/** The remote's Back key on Android; Escape on desktop. */
expect fun KeyEvent.isBackKey(): Boolean

/** How many times a held key has repeated; 0 for the first press. */
expect val KeyEvent.repeatCountCompat: Int

/** Android cancels a key press when focus or the window changes mid-press. */
expect val KeyEvent.isCanceledCompat: Boolean

/** Keeps the display awake while [enabled] (the window's keepScreenOn flag on Android). */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

/** Width of the screen (Android's Configuration.screenWidthDp) or, on desktop, the window. */
@Composable
expect fun rememberScreenWidthDp(): Int

/** Full-window dialog that draws behind system bars on Android. */
expect fun edgeToEdgeDialogProperties(): androidx.compose.ui.window.DialogProperties

/**
 * androidx.compose.ui.window.Dialog with Android's back semantics everywhere: Back reaches the
 * dialog's own BackHandlers first, and dismisses it when none handles it.
 */
@Composable
expect fun Dialog(
    onDismissRequest: () -> Unit,
    properties: androidx.compose.ui.window.DialogProperties = androidx.compose.ui.window.DialogProperties(),
    content: @Composable () -> Unit
)
