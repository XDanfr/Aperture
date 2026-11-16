package me.xdan.aperture.platform

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import coil3.PlatformContext
import java.io.File

actual fun probeLocalMedia(context: PlatformContext, filePath: String): ProbedFile {
    val isContent = filePath.startsWith("content://")
    val uri = if (isContent) Uri.parse(filePath) else null

    val size = runCatching {
        if (uri != null) {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
        } else File(filePath).length().takeIf { it > 0 }
    }.getOrNull()

    val bitrate = runCatching {
        MediaMetadataRetriever().run {
            try {
                if (uri != null) setDataSource(context, uri) else setDataSource(filePath)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            } finally {
                release()
            }
        }
    }.getOrNull()

    val tracks = runCatching {
        val extractor = MediaExtractor()
        try {
            if (uri != null) extractor.setDataSource(context, uri, null) else extractor.setDataSource(filePath)
            (0 until extractor.trackCount).mapNotNull { index -> track(extractor.getTrackFormat(index)) }
        } finally {
            extractor.release()
        }
    }.getOrDefault(emptyList())

    return ProbedFile(sizeBytes = size, bitrate = bitrate, tracks = tracks)
}

private fun track(format: MediaFormat): ProbedTrack? {
    val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
    val kind = when {
        mime.startsWith("video/") -> ProbedTrack.Kind.VIDEO
        mime.startsWith("audio/") -> ProbedTrack.Kind.AUDIO
        mime.startsWith("text/") || mime.startsWith("application/") -> ProbedTrack.Kind.SUBTITLE
        else -> return null
    }
    fun int(key: String) = if (format.containsKey(key)) runCatching { format.getInteger(key) }.getOrNull() else null
    return ProbedTrack(
        kind = kind,
        codec = codecName(mime),
        language = if (format.containsKey(MediaFormat.KEY_LANGUAGE)) format.getString(MediaFormat.KEY_LANGUAGE) else null,
        width = int(MediaFormat.KEY_WIDTH),
        height = int(MediaFormat.KEY_HEIGHT),
        channels = int(MediaFormat.KEY_CHANNEL_COUNT)
    )
}

private fun codecName(mime: String): String = when (mime) {
    "video/avc" -> "H.264"
    "video/hevc" -> "HEVC"
    "video/av01" -> "AV1"
    "video/x-vnd.on2.vp9" -> "VP9"
    "video/mpeg2" -> "MPEG-2"
    "audio/mp4a-latm" -> "AAC"
    "audio/ac3" -> "AC3"
    "audio/eac3" -> "E-AC3"
    "audio/eac3-joc" -> "E-AC3 Atmos"
    "audio/true-hd" -> "TrueHD"
    "audio/vnd.dts", "audio/dts" -> "DTS"
    "audio/vnd.dts.hd" -> "DTS-HD"
    "audio/opus" -> "Opus"
    "audio/flac" -> "FLAC"
    "audio/mpeg" -> "MP3"
    "text/x-ssa" -> "SSA"
    "application/x-subrip", "text/x-subrip" -> "SRT"
    "text/vtt" -> "WebVTT"
    "application/pgs" -> "PGS"
    "application/vobsub" -> "VobSub"
    else -> mime.substringAfter('/').removePrefix("x-").uppercase()
}
