package me.xdan.aperture.ui.screen.player

import me.xdan.aperture.data.local.entity.MediaEntity
import java.io.File

/** What PlayerViewModel needs from the OS around playback. */
interface PlaybackPlatform {
    /** Whether the display can show HDR. */
    val isDisplayHdrCapable: Boolean

    /**
     * Video codecs this device can play, in Jellyfin's names (h264, hevc, vp9, av1, …). A server
     * transcodes anything else.
     */
    val decodableVideoCodecs: Set<String>

    /** Where downloaded subtitles are cached. */
    val cacheDir: File

    /**
     * Inspects a file for formats the device may struggle with, or null when there's nothing to
     * warn about (or the platform decodes everything in software anyway).
     */
    fun inspectCompatibility(location: String): PlaybackCompatibilityWarning?

    /** The URI the engine plays [location] from. */
    fun playbackUri(location: String): String

    /** URI for a local file. */
    fun fileUri(file: File): String

    /** File name without extension of a stored location (path or document URI). */
    fun baseName(location: String): String

    /** Subtitle files beside a media item that came from a granted document tree. */
    fun siblingDocumentSubtitles(media: MediaEntity, videoBaseName: String, extensions: Set<String>): List<SubtitleFile>

    /** A short, non-blocking notice. */
    fun showToast(message: String)
}

data class SubtitleFile(val uri: String, val label: String, val extension: String)
