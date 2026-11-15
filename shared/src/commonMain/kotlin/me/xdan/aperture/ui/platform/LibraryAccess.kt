package me.xdan.aperture.ui.platform

import androidx.compose.runtime.Composable

/**
 * Picking folders for the library. Android opens the system document-tree picker (SAF); desktop
 * opens the OS directory chooser. [isAvailable] is false where no picker exists (some TVs).
 */
class FolderPicker(val isAvailable: Boolean, val launch: () -> Unit)

@Composable
expect fun rememberFolderPicker(onPicked: (String) -> Unit): FolderPicker

/**
 * The onboarding step that gives Aperture something to scan: the video permission on Android,
 * choosing a first folder on desktop. Text is per platform; the panel around it is shared.
 */
class LibraryAccess(
    val isGranted: Boolean,
    val request: () -> Unit,
    val explanation: String,
    val actionLabel: String
)

@Composable
expect fun rememberLibraryAccess(): LibraryAccess
