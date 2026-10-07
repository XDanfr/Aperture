package me.xdan.aperture.ui.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Dialog windows receive keys separately from the Activity. */
@Composable
fun ApertureDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Box(Modifier.onPreviewKeyEvent {
            if (it.key == Key.Escape && properties.dismissOnBackPress) {
                if (it.type == KeyEventType.KeyUp) onDismissRequest()
                true
            } else false
        }, propagateMinConstraints = true) { content() }
    }
}

/** Pointer-only interception leaves D-pad focus and key repeat handling intact. */
@Composable
fun Modifier.onSurfaceTap(onTap: () -> Unit): Modifier {
    val currentTap = rememberUpdatedState(onTap)
    return pointerInput(Unit) { detectTapGestures(onTap = { currentTap.value() }) }
}
