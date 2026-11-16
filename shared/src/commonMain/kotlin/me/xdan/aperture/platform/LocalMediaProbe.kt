package me.xdan.aperture.platform

import coil3.PlatformContext
import me.xdan.aperture.domain.catalog.MediaInfo

/** One track of a local file, as far as the platform can tell. */
data class ProbedTrack(
    val kind: Kind,
    val codec: String? = null,
    val language: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val channels: Int? = null,
    val title: String? = null
) {
    enum class Kind { VIDEO, AUDIO, SUBTITLE }
}

data class ProbedFile(
    val sizeBytes: Long? = null,
    val bitrate: Long? = null,
    val tracks: List<ProbedTrack> = emptyList()
)

/**
 * Reads a local file's size and tracks without playing it. Returns what it can: an unreadable or
 * missing file gives an empty result rather than an error. Call off the main thread.
 */
expect fun probeLocalMedia(context: PlatformContext, filePath: String): ProbedFile

/** Builds the Info dialog's sections for a local file. */
fun localMediaInfo(filePath: String, durationMs: Long?, dateAdded: Long, probe: ProbedFile): MediaInfo {
    val sections = mutableListOf<MediaInfo.Section>()
    sections += MediaInfo.Section("Location", listOf(MediaInfo.Row("Source", "This device"), MediaInfo.Row("Path", displayPath(filePath))))
    val file = buildList {
        filePath.substringAfterLast('.', "").takeIf { it.isNotBlank() && it.length <= 5 }?.let { add(MediaInfo.Row("Container", it.lowercase())) }
        probe.sizeBytes?.takeIf { it > 0 }?.let { add(MediaInfo.Row("Size", MediaInfo.formatSize(it))) }
        durationMs?.takeIf { it > 0 }?.let { add(MediaInfo.Row("Duration", MediaInfo.formatDuration(it))) }
        probe.bitrate?.takeIf { it > 0 }?.let { add(MediaInfo.Row("Bitrate", MediaInfo.formatBitrate(it))) }
        add(MediaInfo.Row("Added", formatDate(dateAdded)))
    }
    sections += MediaInfo.Section("File", file)
    fun tracks(kind: ProbedTrack.Kind) = probe.tracks.filter { it.kind == kind }.map { track ->
        val parts = buildList {
            track.title?.takeIf { it.isNotBlank() }?.let(::add)
            track.language?.takeIf { it.isNotBlank() && it != "und" }?.let(::add)
            if (track.width != null && track.height != null && track.width > 0) add(MediaInfo.resolutionLabel(track.width, track.height))
            track.codec?.let(::add)
            track.channels?.takeIf { it > 0 }?.let { add(MediaInfo.channelLabel(it)) }
        }
        MediaInfo.Row(null, parts.joinToString(" · ").ifBlank { "Unknown" })
    }
    tracks(ProbedTrack.Kind.VIDEO).takeIf { it.isNotEmpty() }?.let { sections += MediaInfo.Section("Video", it) }
    tracks(ProbedTrack.Kind.AUDIO).takeIf { it.isNotEmpty() }?.let { sections += MediaInfo.Section("Audio", it) }
    tracks(ProbedTrack.Kind.SUBTITLE).takeIf { it.isNotEmpty() }?.let { sections += MediaInfo.Section("Subtitles", it) }
    return MediaInfo(sections)
}

/** content:// URIs are unreadable as they are; show the decoded document path instead. */
private fun displayPath(filePath: String): String {
    if (!filePath.startsWith("content://")) return filePath
    val decoded = runCatching { java.net.URLDecoder.decode(filePath, "UTF-8") }.getOrDefault(filePath)
    return decoded.substringAfterLast("/document/", decoded).substringAfter(':', decoded)
        .let { if (it.startsWith("/")) it else "/$it" }
}

internal fun formatDate(epochMs: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(epochMs))
