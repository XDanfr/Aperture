package me.xdan.aperture.ui.screen.player

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import me.xdan.aperture.data.local.entity.MediaEntity
import java.io.File

class AndroidPlaybackPlatform(private val context: Context) : PlaybackPlatform {

    /** Hardware or software decoders; nextlib's FFmpeg renderers cover H.264, HEVC and VP8/9 in software. */
    override val decodableVideoCodecs: Set<String> by lazy {
        val mimes = runCatching {
            android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos
                .filterNot { it.isEncoder }
                .flatMap { it.supportedTypes.toList() }
                .map { it.lowercase() }
                .toSet()
        }.getOrDefault(emptySet())
        buildSet {
            addAll(listOf("h264", "hevc", "vp8", "vp9", "mpeg4", "mpeg2video"))
            if ("video/av01" in mimes) add("av1")
        }
    }

    override val isDisplayHdrCapable: Boolean
        get() {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val display = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val hdrCapabilities = display?.hdrCapabilities
            return hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
        }

    override val cacheDir: File get() = context.cacheDir

    override fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun playbackUri(location: String): String = mediaPlaybackUri(location).toString()

    override fun fileUri(file: File): String = Uri.fromFile(file).toString()

    override fun baseName(location: String): String =
        Uri.decode(Uri.parse(location).lastPathSegment.orEmpty())
            .substringAfterLast('/')
            .substringBeforeLast('.')

    private fun mediaPlaybackUri(location: String): Uri =
        if (location.startsWith("content://")) Uri.parse(location) else Uri.fromFile(File(location))

    override fun inspectCompatibility(location: String): PlaybackCompatibilityWarning? {
        val extractor = MediaExtractor()
        return try {
            val uri = mediaPlaybackUri(location)
            if (uri.scheme == "content") {
                extractor.setDataSource(context, uri, null)
            } else {
                extractor.setDataSource(location)
            }
            var hasDolbyVision = false
            var hasEac3 = false
            var hasTrueHd = false
            var isHevc = false
            var is4k = false
            var hasHdr10 = false
            var is10Bit = false

            repeat(extractor.trackCount) { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME)?.lowercase() ?: return@repeat

                when (mime) {
                    "video/dolby-vision" -> hasDolbyVision = true
                    "audio/eac3", "audio/eac3-joc" -> hasEac3 = true
                    "audio/true-hd" -> hasTrueHd = true
                    "video/hevc" -> {
                        isHevc = true
                        val width = if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                            format.getInteger(MediaFormat.KEY_WIDTH)
                        } else 0
                        val height = if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                            format.getInteger(MediaFormat.KEY_HEIGHT)
                        } else 0
                        if (width >= 3840 || height >= 2160) is4k = true

                        val transfer = if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                            format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                        } else -1
                        if (transfer == MediaFormat.COLOR_TRANSFER_ST2084 ||
                            transfer == MediaFormat.COLOR_TRANSFER_HLG) {
                            hasHdr10 = true
                        }

                        val profile = if (format.containsKey(MediaFormat.KEY_PROFILE)) {
                            format.getInteger(MediaFormat.KEY_PROFILE)
                        } else -1
                        // HEVCProfileMain10 = 2
                        if (profile == 2) is10Bit = true
                    }
                }
            }

            PlaybackCompatibilityWarning(
                hasDolbyVision = hasDolbyVision,
                hasEac3 = hasEac3,
                hasTrueHd = hasTrueHd,
                is4kHevc = is4k && isHevc,
                hasHdr10 = hasHdr10,
                is10Bit = is10Bit
            ).takeIf { it.hasDolbyVision || it.hasEac3 || it.hasTrueHd || it.is4kHevc || it.hasHdr10 || it.is10Bit }
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    override fun siblingDocumentSubtitles(
        media: MediaEntity,
        videoBaseName: String,
        extensions: Set<String>
    ): List<SubtitleFile> {
        val rootUri = media.sourceRootUri?.let(Uri::parse) ?: return emptyList()
        val parentUri = media.parentDocumentUri?.let(Uri::parse) ?: return emptyList()
        val parentDocumentId = runCatching {
            DocumentsContract.getDocumentId(parentUri)
        }.getOrNull() ?: return emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        return runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn) ?: continue
                        val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
                        val nameWithoutExtension = name.substringBeforeLast('.')
                        val matchesVideo = nameWithoutExtension.equals(videoBaseName, ignoreCase = true) ||
                            nameWithoutExtension.startsWith("$videoBaseName.", ignoreCase = true)
                        if (extension in extensions && matchesVideo) {
                            add(
                                SubtitleFile(
                                    uri = DocumentsContract.buildDocumentUriUsingTree(
                                        rootUri,
                                        cursor.getString(idColumn)
                                    ).toString(),
                                    label = nameWithoutExtension,
                                    extension = extension
                                )
                            )
                        }
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}
