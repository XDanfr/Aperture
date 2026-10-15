package me.xdan.aperture.ui.artwork

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Requests and cache access are confined to the owning ViewModel's main dispatcher. */
internal class DynamicAccentController(
    private val scope: CoroutineScope,
    private val resolveArtwork: suspend (Long) -> String?,
    private val loadAccent: suspend (String) -> Int?,
    private val onAccent: (Int) -> Unit
) {
    private val cache = object : LinkedHashMap<String, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean = size > 64
    }
    private var job: Job? = null
    private var activeMediaId: Long? = null

    fun request(mediaId: Long) {
        if (activeMediaId == mediaId && job?.isActive == true) return
        cancel()
        activeMediaId = mediaId
        job = scope.launch {
            // Debounce before the database lookup, including requests whose artwork is cached.
            delay(140)
            val artwork = resolveArtwork(mediaId) ?: return@launch
            ensureActive()
            val accent = cache[artwork] ?: loadAccent(artwork) ?: return@launch
            // A decoder can finish after cancellation; it must never publish a stale palette.
            ensureActive()
            cache[artwork] = accent
            onAccent(accent)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        activeMediaId = null
    }
}
