package me.xdan.aperture.data.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkPathTest {
    @Test
    fun `TMDB artwork is keyed by its path`() {
        assertEquals("/abc.jpg", artworkPathFromUrl("https://image.tmdb.org/t/p/w1280/abc.jpg"))
    }

    @Test
    fun `Jellyfin artwork is keyed by its own URL`() {
        val url = "http://server:8096/Items/0123abcd/Images/Backdrop?tag=ffee&maxWidth=1280&quality=90"
        assertEquals(url, artworkPathFromUrl(url))
    }

    @Test
    fun `other images have no seed`() {
        assertNull(artworkPathFromUrl("http://server:8096/Videos/1/stream?api_key=x"))
        assertNull(artworkPathFromUrl("http://server:8096/Items/1/Images/Primary"))
    }
}
