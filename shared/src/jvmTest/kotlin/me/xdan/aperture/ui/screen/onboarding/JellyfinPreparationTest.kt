package me.xdan.aperture.ui.screen.onboarding

import me.xdan.aperture.data.jellyfin.JellyfinArtworkCache
import me.xdan.aperture.data.jellyfin.JellyfinLibraryWatcher
import me.xdan.aperture.domain.repository.LibraryPreparationProgress
import me.xdan.aperture.domain.repository.LibraryPreparationStage
import org.junit.Assert.assertEquals
import org.junit.Test

class JellyfinPreparationTest {
    private val since = 1_000L

    @Test
    fun `reads the library while it syncs, then counts titles, then is ready`() {
        val syncing = jellyfinPreparation(JellyfinLibraryWatcher.State.Syncing, JellyfinArtworkCache.Progress(), since)
        assertEquals(LibraryPreparationStage.DISCOVERING, syncing.stage)

        val downloading = jellyfinPreparation(
            JellyfinLibraryWatcher.State.Synced(2_000),
            JellyfinArtworkCache.Progress(running = true, total = 40, done = 10, currentTitle = "Alien"),
            since
        )
        assertEquals(LibraryPreparationStage.MATCHING, downloading.stage)
        assertEquals(10, downloading.completedItems)
        assertEquals("Alien", downloading.currentTitle)

        val done = jellyfinPreparation(
            JellyfinLibraryWatcher.State.Synced(2_000),
            JellyfinArtworkCache.Progress(total = 40, done = 40, finishedAt = 3_000),
            since
        )
        assertEquals(LibraryPreparationStage.COMPLETE, done.stage)
    }

    @Test
    fun `a download from before onboarding doesn't count as done`() {
        val earlier = jellyfinPreparation(
            JellyfinLibraryWatcher.State.Syncing,
            JellyfinArtworkCache.Progress(total = 5, done = 5, finishedAt = 500),
            since
        )
        assertEquals(LibraryPreparationStage.DISCOVERING, earlier.stage)
    }

    @Test
    fun `an unreachable server is an error`() {
        val failed = jellyfinPreparation(JellyfinLibraryWatcher.State.Failed("timeout"), JellyfinArtworkCache.Progress(), since)
        assertEquals(LibraryPreparationStage.ERROR, failed.stage)
    }

    @Test
    fun `both sources are ready only when both are`() {
        val local = LibraryPreparationProgress(stage = LibraryPreparationStage.COMPLETE, totalItems = 3, completedItems = 3)
        val server = LibraryPreparationProgress(stage = LibraryPreparationStage.MATCHING, totalItems = 10, completedItems = 4, currentTitle = "Heat")
        val both = combinedPreparation(local, server)
        assertEquals(LibraryPreparationStage.MATCHING, both.stage)
        assertEquals(13, both.totalItems)
        assertEquals(7, both.completedItems)
        assertEquals("Heat", both.currentTitle)
        assertEquals(LibraryPreparationStage.COMPLETE, combinedPreparation(local, server.copy(stage = LibraryPreparationStage.COMPLETE)).stage)
    }
}
