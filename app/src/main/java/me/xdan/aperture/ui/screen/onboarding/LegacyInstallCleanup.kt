package me.xdan.aperture.ui.screen.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.*
import me.xdan.aperture.data.update.LegacyInstallMigration
import me.xdan.aperture.ui.theme.ApertureTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LegacyInstallCleanup(onRecheck: () -> Unit, onCancel: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentRecheck by rememberUpdatedState(onRecheck)
    var launchFailed by remember { mutableStateOf(false) }
    var waitingForUninstall by remember { mutableStateOf(false) }
    val okRequester = remember { FocusRequester() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        waitingForUninstall = false
        currentRecheck()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                waitingForUninstall = false
                currentRecheck()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Dialog(
            onDismissRequest = onCancel,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            LaunchedEffect(waitingForUninstall) {
                withFrameNanos { }
                if (!waitingForUninstall) okRequester.requestFocus()
            }
            Surface(
                modifier = Modifier.width(620.dp),
                shape = ApertureTheme.shapes.dialog,
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(ApertureTheme.spacing.huge), verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)) {
                    Text("Updating Aperture", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Press OK, then select Uninstall (or OK) on the next screen to complete the update.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "This removes the old Aperture app and its settings, My List and watch progress. Your media files stay on your device. We'll ask for media access again and rebuild your library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (launchFailed) {
                        Text(
                            "The uninstall screen couldn't open. Remove the old Aperture in Android Settings, then return here.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)) {
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                        Button(
                            modifier = Modifier.focusRequester(okRequester),
                            enabled = !waitingForUninstall,
                            onClick = {
                                launchFailed = false
                                waitingForUninstall = true
                                try {
                                    launcher.launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:${LegacyInstallMigration.LEGACY_PACKAGE}")))
                                } catch (_: ActivityNotFoundException) {
                                    waitingForUninstall = false
                                    launchFailed = true
                                } catch (_: SecurityException) {
                                    waitingForUninstall = false
                                    launchFailed = true
                                }
                            }
                        ) { Text("OK") }
                    }
                }
            }
        }
    }
}
