package me.xdan.aperture.ui.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Small frames for the scrubbing preview, decoded apart from the active player. */
interface PreviewFrames {
    suspend fun load(sourcePath: String, positionMs: Long): ImageBitmap?
    fun clear()
}

object PreviewFrameLoader {
    private const val QUANTUM_MS = 2_000L

    /** Positions are quantised so scrubbing reuses frames. */
    fun quantise(positionMs: Long): Long =
        (positionMs.coerceAtLeast(0L) / QUANTUM_MS) * QUANTUM_MS
}

@Composable
expect fun rememberPreviewFrames(): PreviewFrames
