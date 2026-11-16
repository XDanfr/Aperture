package me.xdan.aperture.platform

import coil3.PlatformContext
import java.io.File

/** Desktop knows the file's size; tracks would need a probe the desktop build doesn't ship. */
actual fun probeLocalMedia(context: PlatformContext, filePath: String): ProbedFile =
    ProbedFile(sizeBytes = runCatching { File(filePath).length().takeIf { it > 0 } }.getOrNull())
