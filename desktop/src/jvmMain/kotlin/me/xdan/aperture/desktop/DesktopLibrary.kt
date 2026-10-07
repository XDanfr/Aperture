package me.xdan.aperture.desktop

import java.awt.Component
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.Properties
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import me.xdan.aperture.util.CleanedMediaInfo
import me.xdan.aperture.util.FilenameParser

internal data class LibrarySettings(
    val folders: List<String> = emptyList(),
    val favorites: Set<String> = emptySet()
)

internal data class DesktopMedia(val path: String, val info: CleanedMediaInfo) {
    val isEpisode get() = info.episode != null
    val subtitle get() = if (isEpisode) "Season ${info.season} · Episode ${info.episode}" else info.year?.toString().orEmpty()
}

internal data class ScanResult(val media: List<DesktopMedia>, val warnings: List<String>)

/** No Android storage/URI assumptions. Paths remain opaque Unicode strings. */
internal class DesktopLibraryStore(private val file: Path = defaultSettingsPath()) {
    fun load(): LibrarySettings {
        if (!Files.exists(file)) return LibrarySettings()
        val properties = Properties().apply { Files.newInputStream(file).use(::load) }
        fun entries(prefix: String) = properties.stringPropertyNames()
            .filter { it.startsWith(prefix) }.sortedBy { it.substringAfterLast('.').toIntOrNull() ?: 0 }
            .map { properties.getProperty(it) }
        return LibrarySettings(entries("folder."), entries("favorite.").toSet())
    }

    fun save(settings: LibrarySettings) {
        Files.createDirectories(file.parent)
        val properties = Properties()
        settings.folders.forEachIndexed { index, path -> properties.setProperty("folder.$index", path) }
        settings.favorites.sorted().forEachIndexed { index, path -> properties.setProperty("favorite.$index", path) }
        val temporary = Files.createTempFile(file.parent, "aperture-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { properties.store(it, "Aperture desktop library") }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

private fun defaultSettingsPath(): Path {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase(Locale.ROOT)
    val base = when {
        os.contains("win") -> Path.of(System.getenv("APPDATA") ?: home)
        os.contains("mac") -> Path.of(home, "Library", "Application Support")
        else -> Path.of(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config")
    }
    return base.resolve("Aperture").resolve("library.properties")
}

internal suspend fun pickFolder(parent: Component?): String? = withContext(Dispatchers.Swing) {
    val picker = JFileChooser().apply {
        dialogTitle = "Add an Aperture media folder"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        isMultiSelectionEnabled = false
    }
    if (picker.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
        picker.selectedFile.toPath().toRealPath().toString()
    } else null
}

internal suspend fun scanFolders(folders: List<String>): ScanResult = withContext(Dispatchers.IO) {
    val extensions = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "mpg", "mpeg", "wmv")
    val files = linkedMapOf<String, DesktopMedia>()
    val warnings = mutableListOf<String>()
    val context = currentCoroutineContext()
    for (folder in folders) {
        context.ensureActive()
        try {
            // walk does not follow directory symlinks, avoiding cycles and escapes.
            Files.walk(Path.of(folder)).use { paths ->
                val iterator = paths.iterator()
                while (iterator.hasNext()) {
                    context.ensureActive()
                    val path = iterator.next()
                    if (Files.isRegularFile(path) && path.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions) {
                        val canonical = path.toRealPath().toString()
                        files[canonical] = DesktopMedia(canonical, FilenameParser.parse(path.fileName.toString(), canonical))
                    }
                }
            }
        } catch (error: java.io.IOException) {
            warnings += "$folder: ${error.localizedMessage}"
        } catch (error: java.io.UncheckedIOException) {
            warnings += "$folder: ${error.cause?.localizedMessage}"
        } catch (error: SecurityException) {
            warnings += "$folder: ${error.localizedMessage}"
        }
    }
    ScanResult(files.values.sortedWith(compareBy({ it.info.title.lowercase(Locale.ROOT) }, { it.info.season ?: 0 }, { it.info.episode ?: 0 }, { it.path })), warnings)
}

internal suspend fun openMedia(media: DesktopMedia) = withContext(Dispatchers.IO) {
    val file = Path.of(media.path).toFile()
    check(file.isFile) { "This file is no longer available. Reconnect its drive or rescan the library." }
    check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        "No system video player is available. Install a video player and associate it with this file type."
    }
    Desktop.getDesktop().open(file)
}
