package me.xdan.aperture.ui.screen.companion

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.xdan.aperture.companion.CompanionHub

@Composable
internal actual fun rememberLocalNetworkRequest(hub: CompanionHub): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hub.start()
    }
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= 37) launcher.launch(CompanionHub.LOCAL_NETWORK_PERMISSION) else hub.start()
        }
    }
}
