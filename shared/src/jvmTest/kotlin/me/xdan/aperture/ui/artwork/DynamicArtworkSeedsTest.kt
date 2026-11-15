package me.xdan.aperture.ui.artwork

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import me.xdan.aperture.data.local.entity.MediaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DynamicArtworkSeedsTest {
    private fun media(id: Long, path: String) = MediaEntity(id = id, filePath = "$id", title = "$id", type = "MOVIE", backdropPath = path)

    @Test
    fun rapidFocusChangesOnlyLookupTheSettledArtwork() = runTest {
        val active = MutableStateFlow<Long?>(1)
        val resolved = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dynamicArtworkSeeds(active, MutableStateFlow(listOf(media(1, "/a"), media(2, "/b"))), MutableStateFlow("dynamic")) {
                resolved += it; MutableStateFlow(42)
            }.collect {}
        }
        advanceTimeBy(100)
        active.value = 2
        runCurrent()
        advanceTimeBy(139)
        assertTrue(resolved.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("/b"), resolved)
    }

    @Test
    fun replacingArtworkForTheFocusedTitleUpdatesTheSeedWithoutRefocusing() = runTest {
        val items = MutableStateFlow(listOf(media(1, "/old")))
        val resolved = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dynamicArtworkSeeds(MutableStateFlow(1L), items, MutableStateFlow("dynamic")) {
                resolved += it; MutableStateFlow(42)
            }.collect {}
        }
        advanceTimeBy(141)
        items.value = listOf(media(1, "/new").copy(title = "Rematched title"))
        runCurrent()
        advanceTimeBy(141)
        assertEquals(listOf("/old", "/new"), resolved)
    }

    @Test
    fun pendingArtworkUpdatesTheThemeAsSoonAsItsTableRowArrives() = runTest {
        val storedSeed = MutableStateFlow<Int?>(null)
        val published = mutableListOf<Int?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dynamicArtworkSeeds(MutableStateFlow(1L), MutableStateFlow(listOf(media(1, "/pending"))), MutableStateFlow("dynamic")) {
                storedSeed
            }.collect { published += it }
        }
        advanceTimeBy(141)
        assertTrue(published.isEmpty())
        storedSeed.value = 42
        runCurrent()
        assertEquals(listOf(42), published)
    }

    @Test
    fun unrelatedLibraryEditsDoNotReextractUnchangedArtwork() = runTest {
        val items = MutableStateFlow(listOf(media(1, "/same")))
        var lookups = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dynamicArtworkSeeds(MutableStateFlow(1L), items, MutableStateFlow("dynamic")) { lookups++; MutableStateFlow(42) }.collect {}
        }
        advanceTimeBy(141)
        items.value = listOf(media(1, "/same").copy(title = "Renamed"), media(2, "/other"))
        advanceTimeBy(200)
        assertEquals(1, lookups)
    }

    @Test
    fun leavingDynamicModeDiscardsEvenALateExtractionAndReenteringUsesCurrentTitle() = runTest {
        val theme = MutableStateFlow("dynamic")
        val published = mutableListOf<Int?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dynamicArtworkSeeds(MutableStateFlow(1L), MutableStateFlow(listOf(media(1, "/a"))), theme) {
                flow {
                    withContext(NonCancellable) { delay(300) }
                    emit(42)
                }
            }.collect { published += it }
        }
        advanceTimeBy(150)
        theme.value = "purple"
        advanceTimeBy(500)
        assertEquals(listOf<Int?>(null), published)
        theme.value = "dynamic"
        advanceTimeBy(500)
        assertEquals(listOf(null, 42), published)
    }
}
