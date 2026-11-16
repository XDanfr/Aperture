package me.xdan.aperture.ui.platform

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import me.xdan.aperture.companion.CompanionHub

@Composable
actual fun rememberLocalNetworkPermission(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        callback.value(granted)
    }
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= 37) launcher.launch(CompanionHub.LOCAL_NETWORK_PERMISSION) else callback.value(true)
        }
    }
}
