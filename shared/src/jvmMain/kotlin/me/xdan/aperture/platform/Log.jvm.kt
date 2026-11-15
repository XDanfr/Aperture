package me.xdan.aperture.platform

actual object Log {
    private fun print(level: Char, tag: String, message: String, throwable: Throwable? = null): Int {
        System.err.println("$level/$tag: $message")
        throwable?.printStackTrace()
        return 0
    }

    actual fun d(tag: String, message: String) = print('D', tag, message)
    actual fun i(tag: String, message: String) = print('I', tag, message)
    actual fun w(tag: String, message: String) = print('W', tag, message)
    actual fun w(tag: String, message: String, throwable: Throwable?) = print('W', tag, message, throwable)
    actual fun e(tag: String, message: String) = print('E', tag, message)
    actual fun e(tag: String, message: String, throwable: Throwable?) = print('E', tag, message, throwable)
}

actual fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000L
