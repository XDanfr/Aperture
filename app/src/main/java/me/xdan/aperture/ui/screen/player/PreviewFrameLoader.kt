package me.xdan.aperture.ui.screen.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts small video preview frames without moving or disturbing the active
 * Media3 player. Positions are quantised to keep scrubbing inexpensive.
 */
class PreviewFrameLoader(
    private val context: Context,
    private val cacheSize: Int = DEFAULT_CACHE_SIZE
) {
    private val cache = object : LinkedHashMap<Long, Bitmap>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>?): Boolean =
            size > cacheSize
    }

    private var source: String? = null
    private var retriever: MediaMetadataRetriever? = null

    suspend fun load(
        sourcePath: String,
        positionMs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        val quantisedPosition = quantise(positionMs)

        ensureRetriever(sourcePath)

        synchronized(cache) {
            cache[quantisedPosition]?.let { return@withContext it }
        }

        val frame = runCatching {
            retriever?.getFrameAtTime(
                quantisedPosition * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )?.scaleToPreview()
        }.getOrNull()

        if (frame != null) {
            synchronized(cache) {
                cache[quantisedPosition] = frame
            }
        }

        frame
    }

    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
        releaseRetriever()
        source = null
    }

    private fun ensureRetriever(sourcePath: String) {
        if (source == sourcePath && retriever != null) return

        synchronized(cache) {
            cache.values.forEach { bitmap ->
                runCatching { bitmap.recycle() }
            }
            cache.clear()
        }

        releaseRetriever()
        source = sourcePath

        retriever = MediaMetadataRetriever().apply {
            if (sourcePath.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(Uri.parse(sourcePath), "r")
                    ?.use { descriptor ->
                        setDataSource(descriptor.fileDescriptor)
                    }
                    ?: error("Unable to open preview source: $sourcePath")
            } else {
                setDataSource(File(sourcePath).absolutePath)
            }
        }
    }

    private fun releaseRetriever() {
        retriever?.runCatching { release() }
        retriever = null
    }

    companion object {
        private const val DEFAULT_CACHE_SIZE = 10
        private const val QUANTUM_MS = 2_000L

        fun quantise(positionMs: Long): Long =
            (positionMs.coerceAtLeast(0L) / QUANTUM_MS) * QUANTUM_MS
    }
}

private const val PREVIEW_WIDTH = 320
private const val PREVIEW_HEIGHT = 180

private fun Bitmap.scaleToPreview(): Bitmap {
    if (width <= PREVIEW_WIDTH && height <= PREVIEW_HEIGHT) return this

    val scale = minOf(
        PREVIEW_WIDTH.toFloat() / width,
        PREVIEW_HEIGHT.toFloat() / height
    )
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true
    ).also {
        if (it !== this) recycle()
    }
}
