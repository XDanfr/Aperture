package me.xdan.aperture.desktop.mpv

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.util.Locale

/**
 * A silent, paused mpv instance that decodes single frames for the scrubbing preview, apart from
 * the playing instance (the desktop counterpart of Android's MediaMetadataRetriever). Seeks go to
 * keyframes, like OPTION_CLOSEST_SYNC, and frames are cropped to fill like the preview card.
 *
 * Not thread-safe; callers serialise access.
 */
internal class MpvFrameGrabber private constructor(private val lib: LibMpv, private val mpv: Pointer) {
    private val renderContext: Pointer
    private var loadedPath: String? = null

    init {
        val params = RenderParams(LibMpv.RENDER_PARAM_API_TYPE to cString("sw"))
        val result = PointerByReference()
        val status = lib.mpv_render_context_create(result, mpv, params.memory)
        check(status >= 0) { "mpv_render_context_create failed: ${lib.mpv_error_string(status)}" }
        renderContext = result.value
    }

    fun grab(path: String, positionMs: Long, width: Int, height: Int): Image? {
        if (path != loadedPath) {
            lib.mpv_command(mpv, arrayOf("loadfile", path, "replace"))
            if (!awaitEvent(LibMpv.EVENT_FILE_LOADED, failOn = LibMpv.EVENT_END_FILE)) {
                loadedPath = null
                return null
            }
            loadedPath = path
        }
        lib.mpv_command(mpv, arrayOf("seek", String.format(Locale.ROOT, "%.3f", positionMs / 1000.0), "absolute+keyframes"))
        if (!awaitEvent(LibMpv.EVENT_PLAYBACK_RESTART, failOn = LibMpv.EVENT_END_FILE)) return null

        // The decoded frame reaches the render context shortly after the seek completes.
        repeat(50) {
            if (lib.mpv_render_context_update(renderContext) and LibMpv.RENDER_UPDATE_FRAME != 0L) {
                return render(width, height)
            }
            Thread.sleep(10)
        }
        return null
    }

    private fun render(width: Int, height: Int): Image? {
        val rowBytes = (width * 4 + 63) / 64 * 64
        val bitmap = Bitmap().apply {
            allocPixels(ImageInfo(width, height, ColorType.RGB_888X, ColorAlphaType.OPAQUE), rowBytes)
        }
        try {
            val pixels = bitmap.peekPixels() ?: return null
            val size = Memory(8).apply { setInt(0, width); setInt(4, height) }
            val stride = Memory(8).apply { setLong(0, rowBytes.toLong()) }
            val params = RenderParams(
                LibMpv.RENDER_PARAM_SW_SIZE to size,
                LibMpv.RENDER_PARAM_SW_FORMAT to cString("rgb0"),
                LibMpv.RENDER_PARAM_SW_STRIDE to stride,
                LibMpv.RENDER_PARAM_SW_POINTER to Pointer(pixels.addr),
            )
            if (lib.mpv_render_context_render(renderContext, params.memory) < 0) return null
            bitmap.notifyPixelsChanged()
            bitmap.setImmutable()
            return Image.makeFromBitmap(bitmap)
        } finally {
            bitmap.close()
        }
    }

    private fun awaitEvent(id: Int, failOn: Int, timeoutSeconds: Double = 5.0): Boolean {
        val deadline = System.nanoTime() + (timeoutSeconds * 1e9).toLong()
        while (System.nanoTime() < deadline) {
            val event = lib.mpv_wait_event(mpv, 0.25)
            when (event.getInt(LibMpv.EVENT_ID_OFFSET)) {
                id -> return true
                failOn, LibMpv.EVENT_SHUTDOWN -> return false
            }
        }
        return false
    }

    fun release() {
        lib.mpv_render_context_free(renderContext)
        lib.mpv_terminate_destroy(mpv)
    }

    companion object {
        fun create(): MpvFrameGrabber? {
            val lib = LibMpv.instance ?: return null
            val mpv = lib.createHandle() ?: return null
            listOf(
                "vo" to "libmpv", "aid" to "no", "sid" to "no", "pause" to "yes", "idle" to "yes",
                "keep-open" to "always", "config" to "no", "terminal" to "no", "hwdec" to "no",
                "input-default-bindings" to "no",
            ).forEach { (name, value) -> lib.mpv_set_option_string(mpv, name, value) }
            if (lib.mpv_initialize(mpv) < 0) {
                lib.mpv_terminate_destroy(mpv)
                return null
            }
            return runCatching { MpvFrameGrabber(lib, mpv) }.getOrElse {
                lib.mpv_terminate_destroy(mpv)
                null
            }
        }
    }
}
