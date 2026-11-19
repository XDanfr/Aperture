package me.xdan.aperture.mobile.data

import me.xdan.aperture.mobile.ui.screens.newLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewLabelTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    @Test
    fun `only recent titles are called new, by where they arrived`() {
        assertEquals("New on Jellyfin", newLabel(now - 2 * day, fromJellyfin = true, deviceNoun = "TV", now = now))
        assertEquals("New on your TV", newLabel(now - 2 * day, fromJellyfin = false, deviceNoun = "TV", now = now))
        assertNull(newLabel(now - 60 * day, fromJellyfin = true, deviceNoun = "TV", now = now))
        // An older TV sends no date.
        assertNull(newLabel(0, fromJellyfin = false, deviceNoun = "TV", now = now))
    }
}
