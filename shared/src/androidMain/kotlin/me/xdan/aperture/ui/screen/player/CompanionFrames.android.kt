package me.xdan.aperture.ui.screen.player

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

actual class CompanionFrames actual constructor() {
    private val mutex = Mutex()
    private val loader by lazy {
        AndroidPreviewFrameLoader(org.koin.java.KoinJavaComponent.get<Context>(Context::class.java))
    }

    actual suspend fun jpeg(source: String, positionMs: Long): ByteArray? = mutex.withLock {
        val frame = loader.load(source, positionMs) ?: return@withLock null
        ByteArrayOutputStream().use { out ->
            if (frame.isRecycled || !frame.compress(Bitmap.CompressFormat.JPEG, 80, out)) null else out.toByteArray()
        }
    }

    actual fun clear() = loader.clear()
}
