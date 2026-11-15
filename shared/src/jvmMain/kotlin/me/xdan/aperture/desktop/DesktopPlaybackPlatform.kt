package me.xdan.aperture.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.ui.screen.player.PlaybackCompatibilityWarning
import me.xdan.aperture.ui.screen.player.PlaybackPlatform
import me.xdan.aperture.ui.screen.player.SubtitleFile
import java.io.File

class DesktopPlaybackPlatform : PlaybackPlatform {
    private val _toast = MutableStateFlow<Toast?>(null)
    /** The window shows this briefly, like an Android toast. */
    val toast: StateFlow<Toast?> = _toast.asStateFlow()

    data class Toast(val message: String, val id: Long = System.nanoTime())

    // mpv tone-maps HDR to the display itself.
    override val isDisplayHdrCapable: Boolean = false

    override val cacheDir: File get() = DesktopDirectories.cache

    // mpv's FFmpeg decoders handle the formats Android warns about (Dolby Vision, TrueHD, …).
    override fun inspectCompatibility(location: String): PlaybackCompatibilityWarning? = null

    override fun playbackUri(location: String): String = location

    override fun fileUri(file: File): String = file.absolutePath

    override fun baseName(location: String): String = File(location).nameWithoutExtension

    // Desktop files are plain paths, found by PlayerViewModel's sibling search.
    override fun siblingDocumentSubtitles(media: MediaEntity, videoBaseName: String, extensions: Set<String>) =
        emptyList<SubtitleFile>()

    override fun showToast(message: String) {
        _toast.value = Toast(message)
    }
}
