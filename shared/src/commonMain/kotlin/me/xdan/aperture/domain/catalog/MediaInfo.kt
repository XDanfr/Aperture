package me.xdan.aperture.domain.catalog

/**
 * What a details panel's Info dialog shows about one file: where it is, the file itself, and its
 * tracks. Local files and Jellyfin items both fill it, so the dialog looks the same for either.
 */
data class MediaInfo(val sections: List<Section>) {
    data class Section(val title: String, val rows: List<Row>)
    /** [label] is null for list entries such as tracks, which are a single line. */
    data class Row(val label: String?, val value: String)

    companion object {
        fun formatSize(bytes: Long): String {
            val units = listOf("B", "KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var unit = 0
            while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
            return if (unit == 0) "$bytes B" else "%.1f %s".format(value, units[unit])
        }

        fun formatBitrate(bitsPerSecond: Long): String =
            if (bitsPerSecond >= 1_000_000) "%.1f Mbps".format(bitsPerSecond / 1_000_000.0)
            else "${bitsPerSecond / 1000} kbps"

        fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }

        /** "4K", "1080p" and so on, from the frame size. Widescreen films are short, so width counts too. */
        fun resolutionLabel(width: Int, height: Int): String = when {
            width >= 3800 || height >= 2100 -> "4K"
            width >= 2500 || height >= 1400 -> "1440p"
            width >= 1900 || height >= 1000 -> "1080p"
            width >= 1260 || height >= 700 -> "720p"
            height > 0 -> "${height}p"
            else -> "${width}×$height"
        }

        fun channelLabel(channels: Int): String = when (channels) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "$channels ch"
        }
    }
}
