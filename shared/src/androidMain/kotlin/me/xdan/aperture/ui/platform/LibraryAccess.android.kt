package me.xdan.aperture.ui.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@Composable
actual fun rememberFolderPicker(onPicked: (String) -> Unit): FolderPicker {
    val context = LocalContext.current
    val available = remember(context) { hasFolderPicker(context.packageManager) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { onPicked(it.toString()) }
    }
    return remember(available, launcher) { FolderPicker(available) { launcher.launch(null) } }
}

private fun hasFolderPicker(packageManager: PackageManager): Boolean =
    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).resolveActivity(packageManager) != null &&
        packageManager.queryIntentContentProviders(
            Intent(DocumentsContract.PROVIDER_INTERFACE),
            0
        ).isNotEmpty()

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun rememberLibraryAccess(): LibraryAccess {
    val permissionState = rememberPermissionState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )
    return LibraryAccess(
        isGranted = permissionState.status.isGranted,
        request = permissionState::launchPermissionRequest,
        explanation = "Aperture reads your videos to build a local library. It sends titles derived from filenames to TMDB for descriptions and artwork. Your video files are not uploaded. You can revoke video access in Android Settings.",
        actionLabel = "Allow device videos"
    )
}
