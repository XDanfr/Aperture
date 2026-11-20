package me.xdan.aperture.ui.screen.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.xdan.aperture.desktop.mpv.MpvFrameGrabber
import org.jetbrains.skia.EncodedImageFormat

actual class CompanionFrames actual constructor() {
    private val mutex = Mutex()
    private val cache = HashMap<Pair<String, Long>, ByteArray>()

    /**
     * A fresh mpv per frame: over a network stream, a seek in a reused one can hand back the frame
     * from before it, and the phone asks for only a handful (one per chapter), which are cached.
     */
    actual suspend fun jpeg(source: String, positionMs: Long): ByteArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            cache[source to positionMs]?.let { return@withLock it }
            val grabber = MpvFrameGrabber.create() ?: return@withLock null
            try {
                runCatching {
                    grabber.grab(source, positionMs, 320, 180)?.use { image -> image.encodeToData(EncodedImageFormat.JPEG, 80)?.bytes }
                }.getOrNull()?.also { cache[source to positionMs] = it }
            } finally {
                grabber.release()
            }
        }
    }

    actual fun clear() {
        kotlinx.coroutines.runBlocking { mutex.withLock { cache.clear() } }
    }
}
