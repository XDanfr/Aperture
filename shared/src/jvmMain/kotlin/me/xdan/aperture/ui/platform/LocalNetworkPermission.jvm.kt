package me.xdan.aperture.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

// Desktop systems don't gate the local network behind a runtime permission.
@Composable
actual fun rememberLocalNetworkPermission(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember { { callback.value(true) } }
}
