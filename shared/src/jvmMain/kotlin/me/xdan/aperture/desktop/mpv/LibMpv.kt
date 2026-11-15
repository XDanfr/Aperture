package me.xdan.aperture.desktop.mpv

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import me.xdan.aperture.platform.Log
import java.io.File

/**
 * The parts of libmpv's client API (client.h) and render API (render.h) Aperture uses.
 * See https://github.com/mpv-player/mpv/tree/master/include/mpv.
 */
@Suppress("FunctionName")
internal interface LibMpv : Library {
    fun mpv_client_api_version(): Long
    fun mpv_error_string(error: Int): String

    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_wakeup(ctx: Pointer)
    fun mpv_free(data: Pointer)

    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_request_log_messages(ctx: Pointer, minLevel: String): Int

    /** [args] is passed as a NULL-terminated char** (JNA appends the terminator). */
    fun mpv_command(ctx: Pointer, args: Array<String>): Int

    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int

    /** Returns a pointer to an mpv_event owned by mpv until the next call. */
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer

    fun mpv_render_context_create(res: PointerByReference, mpv: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(ctx: Pointer, callback: RenderUpdateCallback?, data: Pointer?)
    fun mpv_render_context_update(ctx: Pointer): Long
    fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int
    fun mpv_render_context_free(ctx: Pointer)

    fun interface RenderUpdateCallback : Callback {
        fun invoke(data: Pointer?)
    }

    companion object {
        const val FORMAT_NONE = 0
        const val FORMAT_STRING = 1
        const val FORMAT_FLAG = 3
        const val FORMAT_INT64 = 4
        const val FORMAT_DOUBLE = 5

        const val EVENT_NONE = 0
        const val EVENT_SHUTDOWN = 1
        const val EVENT_LOG_MESSAGE = 2
        const val EVENT_START_FILE = 6
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val EVENT_SEEK = 20
        const val EVENT_PLAYBACK_RESTART = 21
        const val EVENT_PROPERTY_CHANGE = 22

        const val END_FILE_REASON_EOF = 0
        const val END_FILE_REASON_ERROR = 4

        const val RENDER_PARAM_INVALID = 0
        const val RENDER_PARAM_API_TYPE = 1
        const val RENDER_PARAM_SW_SIZE = 17
        const val RENDER_PARAM_SW_FORMAT = 18
        const val RENDER_PARAM_SW_STRIDE = 19
        const val RENDER_PARAM_SW_POINTER = 20
        const val RENDER_UPDATE_FRAME = 1L

        /** mpv_event: { int event_id; int error; uint64_t reply_userdata; void *data; } */
        const val EVENT_ID_OFFSET = 0L
        const val EVENT_DATA_OFFSET = 16L

        /** mpv_event_property: { const char *name; mpv_format format; void *data; } */
        const val PROPERTY_NAME_OFFSET = 0L
        const val PROPERTY_FORMAT_OFFSET = 8L
        const val PROPERTY_DATA_OFFSET = 16L

        /** mpv_event_log_message: { const char *prefix, *level, *text; int log_level; } */
        const val LOG_PREFIX_OFFSET = 0L
        const val LOG_LEVEL_OFFSET = 8L
        const val LOG_TEXT_OFFSET = 16L

        /** The loaded library, or null when libmpv isn't installed (see [loadError]). */
        val instance: LibMpv? by lazy(::load)

        @Volatile
        var loadError: String? = null
            private set

        /** The file libmpv was loaded from, once [instance] has loaded it. */
        @Volatile
        var loadedFrom: String? = null
            private set

        /** The client API version as "major.minor", once loaded. */
        @Volatile
        var clientApiVersion: String? = null
            private set

        private fun load(): LibMpv? {
            searchDirectories().forEach { dir -> candidateNames().forEach { NativeLibrary.addSearchPath(it, dir.path) } }
            val errors = mutableListOf<String>()
            for (name in candidateNames()) {
                try {
                    val lib = Native.load(name, LibMpv::class.java)
                    val version = lib.mpv_client_api_version()
                    clientApiVersion = "${version shr 16}.${version and 0xFFFF}"
                    loadedFrom = runCatching { NativeLibrary.getInstance(name).file?.absolutePath }.getOrNull() ?: name
                    Log.i("LibMpv", "Loaded $loadedFrom (client API $clientApiVersion)")
                    return lib
                } catch (error: UnsatisfiedLinkError) {
                    errors += "$name: ${error.message}"
                }
            }
            loadError = "libmpv was not found. Install mpv (see docs/DESKTOP.md) or set APERTURE_LIBMPV_DIR.\n" +
                errors.joinToString("\n")
            Log.e("LibMpv", loadError!!)
            return null
        }

        private fun candidateNames(): List<String> = when {
            Platform.isWindows() -> listOf("libmpv-2", "mpv-2", "libmpv-1", "mpv-1", "mpv")
            Platform.isMac() -> listOf("mpv.2", "mpv")
            else -> listOf("mpv", "libmpv.so.2", "libmpv.so.1")
        }

        /**
         * Bundled copies first: the packaged app's resources, where the Windows and macOS builds
         * carry libmpv (see docs/DESKTOP.md). Then the usual package-manager locations; Linux
         * packages depend on the distribution's libmpv instead of bundling it.
         */
        private fun searchDirectories(): List<File> = buildList {
            System.getenv("APERTURE_LIBMPV_DIR")?.let { add(File(it)) }
            System.getProperty("compose.application.resources.dir")?.let { add(File(it)) }
            if (Platform.isMac()) {
                add(File("/opt/homebrew/lib"))
                add(File("/usr/local/lib"))
                add(File("/opt/local/lib"))
            }
            if (Platform.isLinux()) {
                add(File("/app/lib")) // Flatpak
                add(File("/usr/lib64")) // Fedora, openSUSE
                add(File("/usr/lib"))
                add(File("/usr/lib/x86_64-linux-gnu"))
                add(File("/usr/lib/aarch64-linux-gnu"))
                add(File("/usr/local/lib"))
            }
        }.filter(File::isDirectory)
    }
}

/**
 * A new mpv handle. mpv_create() returns null unless LC_NUMERIC is "C", and the JVM, AWT or a GTK
 * look-and-feel may have set it from the user's locale, so it's reset right before each call.
 */
internal fun LibMpv.createHandle(): Pointer? {
    LibC.useCNumericLocale()
    return mpv_create()
}

private interface LibC : Library {
    fun setlocale(category: Int, locale: String): Pointer?

    companion object {
        /** <locale.h>'s LC_NUMERIC: 1 in glibc and musl, 4 on macOS. */
        private val LC_NUMERIC = if (Platform.isMac()) 4 else 1

        private val instance: LibC? by lazy { runCatching { Native.load("c", LibC::class.java) }.getOrNull() }

        fun useCNumericLocale() {
            // Windows builds of mpv don't check the C locale.
            if (Platform.isWindows()) return
            runCatching { instance?.setlocale(LC_NUMERIC, "C") }
        }
    }
}

/** A null-terminated mpv_render_param[] in native memory. Keep it alive until mpv is done with it. */
internal class RenderParams(vararg params: Pair<Int, Pointer?>) {
    // Each entry is { int type; void *data; }: 16 bytes on 64-bit platforms.
    val memory = Memory(16L * (params.size + 1)).apply { clear() }

    init {
        params.forEachIndexed { index, (type, data) ->
            memory.setInt(16L * index, type)
            memory.setPointer(16L * index + 8, data)
        }
        memory.setInt(16L * params.size, LibMpv.RENDER_PARAM_INVALID)
    }
}

internal fun cString(value: String): Memory {
    val bytes = value.toByteArray(Charsets.UTF_8)
    return Memory(bytes.size + 1L).apply {
        write(0, bytes, 0, bytes.size)
        setByte(bytes.size.toLong(), 0)
    }
}
