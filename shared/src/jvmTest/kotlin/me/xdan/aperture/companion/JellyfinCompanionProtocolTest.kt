package me.xdan.aperture.companion

import me.xdan.aperture.companion.protocol.CompanionMedia
import me.xdan.aperture.companion.protocol.CompanionMediaType
import me.xdan.aperture.companion.protocol.CompanionProtocol
import me.xdan.aperture.companion.protocol.MediaSource
import me.xdan.aperture.companion.protocol.tmdbImageUrl
import me.xdan.aperture.ui.screen.player.PlayerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jellyfin titles in the companion protocol, and old and new peers reading each other. */
class JellyfinCompanionProtocolTest {
    @Test
    fun `Jellyfin artwork passes through as a URL while TMDB paths get the image host`() {
        val url = "http://server:8096/Items/abc/Images/Primary?tag=t&maxWidth=500&quality=90"
        assertEquals(url, tmdbImageUrl(url, "w342"))
        assertEquals("https://image.tmdb.org/t/p/w342/poster.jpg", tmdbImageUrl("/poster.jpg", "w342"))
    }

    @Test
    fun `server titles get stable negative ids that can't clash with local ones`() {
        val id = PlayerViewModel.jellyfinDisplayId("0123456789abcdef0123456789abcdef")
        assertTrue(id < 0)
        assertEquals(id, PlayerViewModel.jellyfinDisplayId("0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun `a phone reads a TV from before Jellyfin, and the other way round`() {
        val json = CompanionProtocol.json
        // An older TV sends neither field: the title is local and the source unknown.
        val old = json.decodeFromString(CompanionMedia.serializer(), """{"id":5,"type":"MOVIE","title":"Alien"}""")
        assertEquals(MediaSource.LOCAL, old.source)
        // An older phone ignores the new fields.
        val server = CompanionMedia(id = -7, type = CompanionMediaType.MOVIE, title = "Heat", source = MediaSource.JELLYFIN)
        val encoded = json.encodeToString(CompanionMedia.serializer(), server)
        assertTrue("\"source\":\"jellyfin\"" in encoded)
        assertEquals(server, json.decodeFromString(CompanionMedia.serializer(), encoded))
    }
}
