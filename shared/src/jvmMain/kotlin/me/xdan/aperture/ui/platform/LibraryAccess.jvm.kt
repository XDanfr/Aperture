package me.xdan.aperture.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import me.xdan.aperture.domain.repository.MediaRepository
import org.koin.compose.koinInject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

/** The OS's own directory chooser: a native sheet on macOS, Swing's chooser elsewhere. */
internal fun chooseDirectory(title: String): File? {
    val isMac = System.getProperty("os.name").lowercase().contains("mac")
    if (isMac) {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        try {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            dialog.isVisible = true
            val directory = dialog.directory ?: return null
            val name = dialog.file ?: return null
            return File(directory, name)
        } finally {
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
        }
    }
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

@Composable
actual fun rememberFolderPicker(onPicked: (String) -> Unit): FolderPicker {
    val currentOnPicked by rememberUpdatedState(onPicked)
    return remember {
        FolderPicker(isAvailable = true) {
            chooseDirectory("Choose a video folder")?.let { currentOnPicked(it.absolutePath) }
        }
    }
}

@Composable
actual fun rememberLibraryAccess(): LibraryAccess {
    val repository = koinInject<MediaRepository>()
    val folders by repository.mediaFolders.collectAsState()
    val scope = rememberCoroutineScope()
    return LibraryAccess(
        isGranted = folders.isNotEmpty(),
        request = {
            chooseDirectory("Choose a video folder")?.let { folder ->
                scope.launch { repository.addMediaFolder(folder.absolutePath) }
            }
        },
        explanation = "Aperture builds a local library from the folders you choose. It sends titles derived from filenames to TMDB for descriptions and artwork. Your video files are not uploaded. You can add or remove folders in Settings.",
        actionLabel = "Choose a video folder"
    )
}
