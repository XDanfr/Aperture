package me.xdan.aperture.ui.screen.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.xdan.aperture.desktop.mpv.MpvFrameGrabber
import org.jetbrains.skia.EncodedImageFormat

actual class CompanionFrames actual constructor() {
    private val mutex = Mutex()
    private var grabber: MpvFrameGrabber? = null

    actual suspend fun jpeg(source: String, positionMs: Long): ByteArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val grabber = grabber ?: MpvFrameGrabber.create()?.also { grabber = it } ?: return@withLock null
            runCatching {
                grabber.grab(source, PreviewFrameLoader.quantise(positionMs), 320, 180)?.use { image ->
                    image.encodeToData(EncodedImageFormat.JPEG, 80)?.bytes
                }
            }.getOrNull()
        }
    }

    actual fun clear() {
        val released = grabber ?: return
        grabber = null
        Thread({ kotlinx.coroutines.runBlocking { mutex.withLock { released.release() } } }, "mpv-frames-release").start()
    }
}
