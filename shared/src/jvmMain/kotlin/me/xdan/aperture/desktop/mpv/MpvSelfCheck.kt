package me.xdan.aperture.desktop.mpv

/**
 * `Aperture --check-libmpv`: loads libmpv the way playback does, starts and stops a headless mpv,
 * and reports which file was used. Packaging scripts and CI run it against each installed build.
 */
object MpvSelfCheck {
    class Result(val ok: Boolean, val message: String)

    fun run(): Result {
        val lib = LibMpv.instance ?: return Result(false, LibMpv.loadError ?: "libmpv unavailable")
        val handle = lib.createHandle() ?: return Result(false, "mpv_create failed (${LibMpv.loadedFrom})")
        return try {
            lib.mpv_set_option_string(handle, "vo", "null")
            lib.mpv_set_option_string(handle, "ao", "null")
            lib.mpv_set_option_string(handle, "config", "no")
            val status = lib.mpv_initialize(handle)
            if (status < 0) {
                Result(false, "mpv_initialize failed: ${lib.mpv_error_string(status)} (${LibMpv.loadedFrom})")
            } else {
                val version = lib.mpv_get_property_string(handle, "mpv-version")
                    ?.let { pointer -> pointer.getString(0).also { lib.mpv_free(pointer) } }
                Result(true, "libmpv OK: ${LibMpv.loadedFrom} ($version, client API ${LibMpv.clientApiVersion})")
            }
        } finally {
            lib.mpv_terminate_destroy(handle)
        }
    }
}
