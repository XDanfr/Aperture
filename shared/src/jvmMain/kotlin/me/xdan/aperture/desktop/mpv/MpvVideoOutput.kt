package me.xdan.aperture.desktop.mpv

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.xdan.aperture.platform.Log
import me.xdan.aperture.ui.screen.player.VideoResizeModes
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Frames from libmpv's software renderer, drawn by Compose.
 *
 * mpv renders on its own thread straight into the back of two Skia bitmaps; the front one is what
 * Compose draws. Rendering in software keeps video an ordinary layer in the Compose scene, so the
 * shared player OSD, quick menu and subtitles draw over it exactly as they do over Android's
 * PlayerView. Decoding still uses the GPU where available (hwdec=auto-copy).
 */
internal class MpvVideoOutput(private val lib: LibMpv, private val mpv: Pointer) {
    private val renderContext: Pointer
    // JNA only keeps a weak reference to callbacks; this field keeps it alive.
    private val updateCallback = LibMpv.RenderUpdateCallback { requestRender() }

    private val swapLock = ReentrantLock()
    private val wake = Object()
    private var renderPending = false
    @Volatile private var running = true
    @Volatile private var targetWidth = 0
    @Volatile private var targetHeight = 0

    private var front: Bitmap? = null
    private var back: Bitmap? = null

    private val _frames = MutableStateFlow(0L)
    /** Increments each time a new frame is ready to draw. */
    val frames: StateFlow<Long> = _frames.asStateFlow()

    private val renderThread: Thread

    init {
        val apiType = cString("sw")
        val params = RenderParams(LibMpv.RENDER_PARAM_API_TYPE to apiType)
        val result = PointerByReference()
        val status = lib.mpv_render_context_create(result, mpv, params.memory)
        check(status >= 0) { "mpv_render_context_create failed: ${lib.mpv_error_string(status)}" }
        renderContext = result.value
        lib.mpv_render_context_set_update_callback(renderContext, updateCallback, null)
        renderThread = Thread(::renderLoop, "mpv-render").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * The surface's size in pixels. Rendering is capped at 1080p (keeping the window's shape) and
     * Skia scales the rest of the way, which keeps software rendering cheap on 4K displays.
     */
    fun setTargetSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val scale = min(1f, min(MAX_RENDER_WIDTH.toFloat() / width, MAX_RENDER_HEIGHT.toFloat() / height))
        val w = (width * scale).roundToInt().coerceAtLeast(1)
        val h = (height * scale).roundToInt().coerceAtLeast(1)
        if (w == targetWidth && h == targetHeight) return
        targetWidth = w
        targetHeight = h
        requestRender()
    }

    private fun requestRender() {
        synchronized(wake) {
            renderPending = true
            wake.notifyAll()
        }
    }

    private fun renderLoop() {
        // R, G, B and a padding byte, which mpv leaves at 0. Skia's RGB_888X ignores that byte;
        // declaring BGRA as opaque instead would carry the 0 through as alpha and draw nothing.
        val format = cString("rgb0")
        val size = Memory(8)
        val stride = Memory(8)
        while (running) {
            synchronized(wake) {
                while (!renderPending && running) wake.wait()
                renderPending = false
            }
            if (!running) break
            val flags = lib.mpv_render_context_update(renderContext)
            val width = targetWidth
            val height = targetHeight
            if (width <= 0 || height <= 0) continue
            val target = back?.takeIf { it.width == width && it.height == height }
            val resized = target == null
            if (flags and LibMpv.RENDER_UPDATE_FRAME == 0L && !resized) continue

            val bitmap = target ?: newBitmap(width, height).also { fresh ->
                back?.close()
                back = fresh
            }
            val pixels = bitmap.peekPixels() ?: continue
            size.setInt(0, width)
            size.setInt(4, height)
            stride.setLong(0, bitmap.rowBytes.toLong())
            val params = RenderParams(
                LibMpv.RENDER_PARAM_SW_SIZE to size,
                LibMpv.RENDER_PARAM_SW_FORMAT to format,
                LibMpv.RENDER_PARAM_SW_STRIDE to stride,
                LibMpv.RENDER_PARAM_SW_POINTER to Pointer(pixels.addr),
            )
            val status = lib.mpv_render_context_render(renderContext, params.memory)
            if (status < 0) {
                Log.w(TAG, "Render failed: ${lib.mpv_error_string(status)}")
                continue
            }
            bitmap.notifyPixelsChanged()
            swapLock.withLock {
                back = front
                front = bitmap
            }
            _frames.value++
        }
    }

    private fun newBitmap(width: Int, height: Int): Bitmap {
        // Rows aligned to 64 bytes let mpv use its fast SIMD paths.
        val rowBytes = (width * 4 + 63) / 64 * 64
        return Bitmap().apply {
            allocPixels(ImageInfo(width, height, ColorType.RGB_888X, ColorAlphaType.OPAQUE), rowBytes)
        }
    }

    /** Draws the latest frame into [dst] on [canvas]. Called on the UI thread. */
    fun draw(canvas: Canvas, dst: Rect) {
        swapLock.withLock {
            val frame = front ?: return
            Image.makeFromBitmap(frame).use { image ->
                canvas.drawImageRect(
                    image,
                    Rect.makeWH(frame.width.toFloat(), frame.height.toFloat()),
                    dst,
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
                    paint,
                    true
                )
            }
        }
    }

    /** Fit, stretch or crop to fill; [mode] is one of [VideoResizeModes]. */
    fun setResizeMode(mode: Int) {
        val (keepAspect, panScan) = when (mode) {
            VideoResizeModes.FILL -> "no" to "0"
            VideoResizeModes.ZOOM -> "yes" to "1"
            else -> "yes" to "0"
        }
        lib.mpv_set_property_string(mpv, "keepaspect", keepAspect)
        lib.mpv_set_property_string(mpv, "panscan", panScan)
    }

    /** Must run before the mpv handle is destroyed. */
    fun release() {
        running = false
        requestRender()
        renderThread.join(2_000)
        lib.mpv_render_context_set_update_callback(renderContext, null, null)
        lib.mpv_render_context_free(renderContext)
        swapLock.withLock {
            front?.close()
            back?.close()
            front = null
            back = null
        }
    }

    private companion object {
        const val TAG = "MpvVideoOutput"
        const val MAX_RENDER_WIDTH = 1920
        const val MAX_RENDER_HEIGHT = 1080
        val paint = Paint()
    }
}
