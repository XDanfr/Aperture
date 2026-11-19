package me.xdan.aperture.desktop

import me.xdan.aperture.data.library.DiscoveredFile
import me.xdan.aperture.data.library.MediaLibrarySource
import me.xdan.aperture.data.library.VideoFiles
import me.xdan.aperture.domain.repository.MediaFolder
import java.io.File
import java.nio.file.Files

/**
 * The desktop library is exactly the folders the user picked. Folder identifiers are absolute
 * paths; hidden entries are skipped (as on Android) and symlinked folders are not followed, so a
 * link back up the tree can't loop the scan.
 */
class DesktopMediaLibrarySource : MediaLibrarySource {

    // Nothing is indexed implicitly; there's no system video index to read.
    override suspend fun discoverSystemMedia(onFile: suspend (DiscoveredFile) -> Unit) = Unit

    override suspend fun grantFolder(folder: String) {
        val directory = File(folder)
        require(directory.isDirectory && directory.canRead()) { "Aperture could not read that folder" }
    }

    override fun releaseFolder(folder: String) = Unit

    override fun isGone(location: String, parentLocation: String?): Boolean {
        val file = File(location)
        return !file.exists() && file.parentFile?.isDirectory == true
    }

    override suspend fun walkFolder(folder: String, onFile: suspend (DiscoveredFile) -> Unit): Boolean {
        val root = File(folder)
        if (!root.isDirectory || !root.canRead()) return false

        suspend fun scan(directory: File) {
            val children = directory.listFiles() ?: return
            for (child in children.sortedBy { it.name.lowercase() }) {
                if (child.name.startsWith('.') || child.isHidden) continue
                if (child.isDirectory) {
                    if (!Files.isSymbolicLink(child.toPath())) scan(child)
                } else if (child.isFile && VideoFiles.isVideoFile(child.name)) {
                    onFile(
                        DiscoveredFile(
                            name = child.name,
                            location = child.absolutePath,
                            parentLocation = directory.absolutePath
                        )
                    )
                }
            }
        }

        scan(root)
        return true
    }

    override fun describeFolder(folder: String): MediaFolder {
        val directory = File(folder)
        return MediaFolder(
            uri = folder,
            name = directory.name.ifBlank { directory.path },
            isAvailable = directory.isDirectory && directory.canRead()
        )
    }

    override fun fileName(location: String): String = File(location).name

    override fun parserPath(location: String): String = location
}
