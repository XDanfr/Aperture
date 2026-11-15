package me.xdan.aperture.ui.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.xdan.aperture.desktop.mpv.MpvFrameGrabber

/** Same cache size, quantum and 320×180 frame as the Android loader. */
private class DesktopPreviewFrames : PreviewFrames {
    private val mutex = Mutex()
    private var grabber: MpvFrameGrabber? = null
    private var source: String? = null
    private val cache = object : LinkedHashMap<Long, ImageBitmap>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ImageBitmap>?) = size > CACHE_SIZE
    }

    override suspend fun load(sourcePath: String, positionMs: Long): ImageBitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val position = PreviewFrameLoader.quantise(positionMs)
            if (source != sourcePath) {
                cache.clear()
                source = sourcePath
            }
            cache[position]?.let { return@withLock it }
            val grabber = grabber ?: MpvFrameGrabber.create()?.also { grabber = it } ?: return@withLock null
            val frame = runCatching { grabber.grab(sourcePath, position, 320, 180) }.getOrNull()
                ?.use { it.toComposeImageBitmap() }
            frame?.also { cache[position] = it }
        }
    }

    override fun clear() {
        val released = grabber
        grabber = null
        source = null
        cache.clear()
        // Off the UI thread, and only once any in-flight grab has finished with it.
        if (released != null) {
            Thread({ kotlinx.coroutines.runBlocking { mutex.withLock { released.release() } } }, "mpv-preview-release").start()
        }
    }

    private companion object {
        const val CACHE_SIZE = 10
    }
}

@Composable
actual fun rememberPreviewFrames(): PreviewFrames = remember { DesktopPreviewFrames() }
