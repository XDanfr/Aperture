package me.xdan.aperture.data.library

import me.xdan.aperture.domain.repository.MediaFolder

/**
 * Where media files come from. The repository owns matching, metadata and the database; a platform
 * only finds files. Android reads MediaStore, well-known storage folders and SAF trees; desktop
 * walks folders the user picked.
 *
 * Folder identifiers are opaque strings: tree URIs on Android, absolute paths on desktop.
 */
interface MediaLibrarySource {
    /** Media the platform indexes without the user picking a folder. */
    suspend fun discoverSystemMedia(onFile: suspend (DiscoveredFile) -> Unit)

    /** Take whatever long-lived access [folder] needs; throws when it can't be read. */
    suspend fun grantFolder(folder: String)

    /** Give up long-lived access to [folder]. */
    fun releaseFolder(folder: String)

    /**
     * Walk [folder] recursively, skipping hidden entries, reporting each file. Returns false when
     * the folder can't be read at all.
     */
    suspend fun walkFolder(folder: String, onFile: suspend (DiscoveredFile) -> Unit): Boolean

    /** Display name and availability for a selected folder. */
    fun describeFolder(folder: String): MediaFolder

    /**
     * Whether the file at [location] has been deleted: its folder ([parentLocation], or the path's
     * parent) is still there and the file isn't. A drive that's been unplugged isn't "gone", so its
     * titles come back when it does. False when that can't be told.
     */
    fun isGone(location: String, parentLocation: String? = null): Boolean = false

    /** File name of a stored media location, for re-parsing titles. */
    fun fileName(location: String): String

    /** Path the filename parser should see for [location], or null to use the name alone. */
    fun parserPath(location: String): String?
}

data class DiscoveredFile(
    val name: String,
    val location: String,
    val durationMs: Long = 0L,
    /** Root-relative path for the filename parser, when [location] isn't a plain path. */
    val parserPath: String = location,
    val parentLocation: String? = null
)

/** Shared by every platform's file walker. */
object VideoFiles {
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm")

    fun isVideoFile(name: String): Boolean =
        name.substringAfterLast('.', missingDelimiterValue = "").lowercase() in VIDEO_EXTENSIONS
}
