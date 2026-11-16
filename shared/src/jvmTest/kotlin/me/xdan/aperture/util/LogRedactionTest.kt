package me.xdan.aperture.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LogRedactionTest {
    @Test
    fun `hides the access token and keeps the rest`() {
        assertEquals(
            "http://server/Videos/1/stream?static=true&mediaSourceId=1&api_key=…",
            redactUrl("http://server/Videos/1/stream?static=true&mediaSourceId=1&api_key=abc123")
        )
        assertEquals("http://server/master.m3u8?api_key=…&VideoCodec=h264", redactUrl("http://server/master.m3u8?api_key=abc&VideoCodec=h264"))
        assertEquals("/local/file.mkv", redactUrl("/local/file.mkv"))
    }
}
