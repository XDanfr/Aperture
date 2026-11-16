package me.xdan.aperture.data.library

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import me.xdan.aperture.domain.repository.MediaFolder
import java.io.File

/** MediaStore volumes, common storage folders, and SAF trees the user granted. */
class AndroidMediaLibrarySource(private val context: Context) : MediaLibrarySource {

    override suspend fun discoverSystemMedia(onFile: suspend (DiscoveredFile) -> Unit) {
        // Android TV devices can expose removable USB storage as separate
        // MediaStore volumes. Scan each volume rather than only the primary
        // external volume so indexed USB media is included as well.
        runCatching {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION
            )

            val videoUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.getExternalVolumeNames(context).map { volumeName ->
                    MediaStore.Video.Media.getContentUri(volumeName)
                }
            } else {
                listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            }

            videoUris.forEach { videoUri ->
                context.contentResolver.query(videoUri, projection, null, null, null)?.use {
                    val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                    while (it.moveToNext()) {
                        onFile(
                            DiscoveredFile(
                                name = it.getString(nameColumn),
                                location = it.getString(dataColumn)?.takeIf { path -> path.isNotBlank() }
                                    ?: ContentUris.withAppendedId(videoUri, it.getLong(idColumn)).toString(),
                                durationMs = it.getLong(durationColumn)
                            )
                        )
                    }
                }
            }
        }.onFailure { it.printStackTrace() }

        runCatching {
            MediaScanner.scanDirectories().forEach { file ->
                onFile(DiscoveredFile(file.name, file.absolutePath, 0L))
            }
        }.onFailure { it.printStackTrace() }
    }

    override suspend fun grantFolder(folder: String) {
        val treeUri = Uri.parse(folder)
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val document = DocumentFile.fromTreeUri(context, treeUri)
        require(document != null && document.isDirectory && document.canRead()) {
            "Aperture could not read that folder"
        }
    }

    override fun isGone(location: String, parentLocation: String?): Boolean = runCatching {
        if (location.startsWith("content://")) {
            // A folder picked through the system picker: gone if its folder is readable and the
            // file isn't there. Without a known folder (a MediaStore entry), it can't be told.
            val parent = parentLocation?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) } ?: return@runCatching false
            if (!parent.exists() || !parent.canRead()) return@runCatching false
            DocumentFile.fromSingleUri(context, Uri.parse(location))?.exists() == false
        } else {
            val file = File(location)
            !file.exists() && file.parentFile?.isDirectory == true
        }
    }.getOrDefault(false)

    override fun releaseFolder(folder: String) {
        context.contentResolver.releasePersistableUriPermission(
            Uri.parse(folder),
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    override suspend fun walkFolder(folder: String, onFile: suspend (DiscoveredFile) -> Unit): Boolean {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(folder)) ?: return false
        if (!root.isDirectory || !root.canRead()) return false
        val rootName = root.name?.takeIf { it.isNotBlank() } ?: "Selected media"

        suspend fun scanDirectory(directory: DocumentFile, relativeParts: List<String>) {
            directory.listFiles().forEach { document ->
                val name = document.name ?: return@forEach
                if (name.startsWith('.')) return@forEach
                if (document.isDirectory) {
                    scanDirectory(document, relativeParts + name)
                } else if (document.isFile && VideoFiles.isVideoFile(name)) {
                    onFile(
                        DiscoveredFile(
                            name = name,
                            location = document.uri.toString(),
                            parserPath = (listOf(rootName) + relativeParts + name)
                                .joinToString(separator = "/", prefix = "/"),
                            parentLocation = directory.uri.toString()
                        )
                    )
                }
            }
        }

        scanDirectory(root, emptyList())
        return true
    }

    override fun describeFolder(folder: String): MediaFolder {
        val document = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(folder)) }.getOrNull()
        return MediaFolder(
            uri = folder,
            name = document?.name?.takeIf { it.isNotBlank() }
                ?: Uri.decode(Uri.parse(folder).lastPathSegment.orEmpty())
                    .substringAfterLast(':')
                    .ifBlank { "Media folder" },
            isAvailable = document?.let { it.isDirectory && it.canRead() } == true
        )
    }

    override fun fileName(location: String): String = if (location.startsWith("content://")) {
        Uri.decode(Uri.parse(location).lastPathSegment.orEmpty()).substringAfterLast('/')
    } else {
        File(location).name
    }

    override fun parserPath(location: String): String? = location.takeUnless { it.startsWith("content://") }
}
