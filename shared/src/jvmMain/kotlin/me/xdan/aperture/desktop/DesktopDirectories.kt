package me.xdan.aperture.desktop

import java.io.File

/** Where the desktop app keeps its data, following each OS's conventions. */
object DesktopDirectories {
    private val os = System.getProperty("os.name").lowercase()
    private val home = File(System.getProperty("user.home"))

    /** Settings, the library database and preferences. */
    val data: File by lazy {
        when {
            "mac" in os -> File(home, "Library/Application Support/Aperture")
            "win" in os -> File(System.getenv("APPDATA") ?: home.path, "Aperture")
            else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, "aperture")
        }.also { it.mkdirs() }
    }

    /** Artwork and downloaded subtitles; safe to delete. */
    val cache: File by lazy {
        when {
            "mac" in os -> File(home, "Library/Caches/Aperture")
            "win" in os -> File(System.getenv("LOCALAPPDATA") ?: data.path, "Aperture/Cache")
            else -> File(System.getenv("XDG_CACHE_HOME") ?: File(home, ".cache").path, "aperture")
        }.also { it.mkdirs() }
    }
}
