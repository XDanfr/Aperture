package me.xdan.aperture.platform

/** android.util.Log on Android; standard error on desktop. */
expect object Log {
    fun d(tag: String, message: String): Int
    fun i(tag: String, message: String): Int
    fun w(tag: String, message: String): Int
    fun w(tag: String, message: String, throwable: Throwable?): Int
    fun e(tag: String, message: String): Int
    fun e(tag: String, message: String, throwable: Throwable?): Int
}

/** Milliseconds since an arbitrary fixed point; only differences are meaningful. */
expect fun elapsedRealtime(): Long
