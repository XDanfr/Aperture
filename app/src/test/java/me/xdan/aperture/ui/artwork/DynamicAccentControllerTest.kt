package me.xdan.aperture.ui.artwork

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DynamicAccentControllerTest {
    @Test
    fun rapidFocusChangesOnlyResolveTheSettledTitle() = runTest {
        val resolved = mutableListOf<Long>()
        val published = mutableListOf<Int>()
        val controller = DynamicAccentController(this, {
            resolved += it
            "$it"
        }, { it.toInt() }, published::add)
        controller.request(1)
        advanceTimeBy(100)
        controller.request(2)
        advanceTimeBy(139)
        runCurrent()
        assertTrue(resolved.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf(2L), resolved)
        assertEquals(listOf(2), published)
    }

    @Test
    fun focusChangeCancelsTheDatabaseLookup() = runTest {
        val loaded = mutableListOf<String>()
        val controller = DynamicAccentController(this, {
            delay(500)
            "$it"
        }, { loaded += it; it.toInt() }, {})
        controller.request(1)
        advanceTimeBy(200)
        controller.request(2)
        advanceUntilIdle()
        assertEquals(listOf("2"), loaded)
    }

    @Test
    fun lateDecoderCompletionCannotOverwriteTheNewAccent() = runTest {
        val published = mutableListOf<Int>()
        val controller = DynamicAccentController(this, { "$it" }, {
            if (it == "1") withContext(NonCancellable) { delay(500) }
            it.toInt()
        }, published::add)
        controller.request(1)
        advanceTimeBy(200)
        controller.request(2)
        advanceUntilIdle()
        assertEquals(listOf(2), published)
    }

    @Test
    fun leavingDynamicThemeCancelsPendingWork() = runTest {
        val published = mutableListOf<Int>()
        val controller = DynamicAccentController(this, { "$it" }, {
            withContext(NonCancellable) { delay(500) }
            1
        }, published::add)
        controller.request(1)
        advanceTimeBy(200)
        controller.cancel()
        advanceUntilIdle()
        assertTrue(published.isEmpty())
    }

    @Test
    fun cacheReusesArtworkAndEvictsTheLeastRecentlyUsedEntry() = runTest {
        val loaded = mutableListOf<String>()
        val controller = DynamicAccentController(this, { "$it" }, {
            loaded += it
            it.toInt()
        }, {})
        suspend fun request(id: Long) {
            controller.request(id)
            advanceUntilIdle()
        }
        for (id in 1L..64L) request(id)
        request(1)
        request(65)
        request(1)
        assertEquals(65, loaded.size)
        request(2)
        assertEquals(66, loaded.size)
        assertEquals("2", loaded.last())
    }

    @Test
    fun failedExtractionCanBeRetriedForTheSameTitle() = runTest {
        var attempts = 0
        val published = mutableListOf<Int>()
        val controller = DynamicAccentController(this, { "shared-artwork" }, {
            if (++attempts == 1) null else 42
        }, published::add)
        controller.request(1)
        advanceUntilIdle()
        controller.request(1)
        advanceUntilIdle()
        controller.request(2)
        advanceUntilIdle()
        assertEquals(2, attempts)
        assertEquals(listOf(42, 42), published)
    }
}
