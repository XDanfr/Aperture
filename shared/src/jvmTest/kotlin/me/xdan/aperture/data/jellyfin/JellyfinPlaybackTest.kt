package me.xdan.aperture.data.jellyfin

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.xdan.aperture.companion.CompanionPlatform
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.data.local.entity.JellyfinAccountEntity
import me.xdan.aperture.data.local.entity.RemoteItemEntity
import me.xdan.aperture.platform.PropertiesStores
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** Playback negotiation and reporting against a fake server. */
class JellyfinPlaybackTest {
    private val temp: File = Files.createTempDirectory("jellyfin-playback").toFile()
    private val server = MockWebServer()
    private val requests = mutableListOf<Pair<String, String>>()
    /** The PlaybackInfo reply's media source; tests set what the server offers. */
    private var source = """{"Id":"src1","ETag":"etag1","Container":"mkv","RunTimeTicks":72000000000,
        "SupportsDirectPlay":true,"SupportsDirectStream":true,"SupportsTranscoding":true,
        "MediaStreams":[
          {"Type":"Video","Index":0,"Codec":"hevc","IsDefault":true,"IsForced":false,"IsHearingImpaired":false,"IsExternal":false,"IsTextSubtitleStream":false,"SupportsExternalStream":false},
          {"Type":"Subtitle","Index":2,"Codec":"subrip","Language":"eng","DisplayTitle":"English - SUBRIP","DeliveryMethod":"External","DeliveryUrl":"/Videos/x/src1/Subtitles/2/0/Stream.srt","IsDefault":false,"IsForced":false,"IsHearingImpaired":false,"IsExternal":true,"IsTextSubtitleStream":true,"SupportsExternalStream":true},
          {"Type":"Subtitle","Index":3,"Codec":"PGSSUB","DeliveryMethod":"Embed","IsDefault":false,"IsForced":false,"IsHearingImpaired":false,"IsExternal":false,"IsTextSubtitleStream":false,"SupportsExternalStream":false}
        ]}"""
    private lateinit var database: AppDatabase
    private lateinit var account: JellyfinAccountEntity
    private lateinit var playback: JellyfinPlayback
    private val movieId = id(1)

    @Before
    fun setUp() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                synchronized(requests) { requests += path to (request.body?.utf8() ?: "") }
                return when {
                    path.endsWith("/PlaybackInfo") -> json("""{"MediaSources":[${complete(source)}],"PlaySessionId":"session1"}""")
                    path.startsWith("/Sessions/Playing") -> MockResponse.Builder().code(204).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        database = Room.databaseBuilder<AppDatabase>(File(temp, "db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        val credentials = JellyfinCredentials(PropertiesStores(File(temp, "prefs")))
        val clients = JellyfinClients(
            createJellyfin { clientInfo = ClientInfo("Aperture", "test") },
            credentials,
            object : CompanionPlatform {
                override val hasLocalNetworkPermission = true
                override val deviceName = "Test TV"
                override fun localIpv4Address(): String? = null
                override fun advertise(serviceName: String, port: Int, txt: Map<String, String>) = Unit
                override fun stopAdvertising() = Unit
            }
        )
        val accountId = database.jellyfinDao().insertAccount(
            JellyfinAccountEntity(
                profileId = 1, serverId = "server", serverName = "Fake", baseUrl = server.url("/").toString().trimEnd('/'),
                userId = id(99), userName = "viewer"
            )
        )
        credentials.saveToken(accountId, "token")
        account = database.jellyfinDao().account(accountId)!!
        playback = JellyfinPlayback(clients, database.jellyfinDao(), MutableStateFlow(account))
        database.jellyfinDao().upsertItems(
            listOf(RemoteItemEntity(accountId = account.id, itemId = movieId, type = "MOVIE", name = "Film", runtimeMs = 7_200_000, playbackPositionMs = 600_000))
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
        temp.deleteRecursively()
    }

    @Test
    fun `direct play streams the original file and side-loads text subtitles`() = runBlocking {
        val stream = playback.prepare(account, movieId, setOf("h264", "hevc"), startPositionMs = 600_000)!!

        assertEquals(PlayMethod.DIRECT_PLAY, stream.method)
        assertTrue(stream.url.startsWith(server.url("/").toString().trimEnd('/') + "/Videos/$movieId/stream?static=true"))
        assertTrue("mediaSourceId=src1" in stream.url && "tag=etag1" in stream.url && "api_key=token" in stream.url)
        assertEquals("session1", stream.playSessionId)
        assertEquals(7_200_000L, stream.durationMs)
        assertEquals(600_000L, stream.startPositionMs)
        // The SRT is delivered as a file; the PGS stays in the container.
        val subtitle = stream.subtitles.single()
        assertEquals("English - SUBRIP", subtitle.label)
        assertEquals("application/x-subrip", subtitle.mimeType)
        assertTrue(subtitle.url.endsWith("/Videos/x/src1/Subtitles/2/0/Stream.srt?api_key=token"))

        val body = Json.parseToJsonElement(requests.single { it.first.endsWith("/PlaybackInfo") }.second).jsonObject
        assertEquals(6_000_000_000L, body["StartTimeTicks"]!!.jsonPrimitive.long)
        assertTrue(body["EnableDirectPlay"]!!.jsonPrimitive.boolean)
        val profile = body["DeviceProfile"]!!.jsonObject
        val direct = profile["DirectPlayProfiles"]!!.jsonArray.single().jsonObject
        assertEquals("h264,hevc", direct["VideoCodec"]!!.jsonPrimitive.content)
    }

    @Test
    fun `asks for a transcode when direct play isn't allowed`() = runBlocking {
        source = """{"Id":"src1","SupportsDirectPlay":false,"SupportsDirectStream":false,"SupportsTranscoding":true,
            "TranscodingUrl":"/videos/$movieId/master.m3u8?MediaSourceId=src1&VideoCodec=h264","TranscodingSubProtocol":"hls"}"""

        val stream = playback.prepare(account, movieId, setOf("h264"), 0, allowDirect = false)!!

        assertEquals(PlayMethod.TRANSCODE, stream.method)
        assertTrue(stream.url.endsWith("/videos/$movieId/master.m3u8?MediaSourceId=src1&VideoCodec=h264&api_key=token"))
        val body = Json.parseToJsonElement(requests.last { it.first.endsWith("/PlaybackInfo") }.second).jsonObject
        assertFalse(body["EnableDirectPlay"]!!.jsonPrimitive.boolean)
        assertFalse(body["EnableDirectStream"]!!.jsonPrimitive.boolean)
        // Only codecs the device has are offered for the transcode.
        val transcoding = body["DeviceProfile"]!!.jsonObject["TranscodingProfiles"]!!.jsonArray.single().jsonObject
        assertEquals("h264", transcoding["VideoCodec"]!!.jsonPrimitive.content)
    }

    @Test
    fun `nothing to play when the server is unreachable`() = runBlocking {
        server.close()
        assertNull(playback.prepare(account, movieId, setOf("h264"), 0))
    }

    @Test
    fun `stopping near the end reports it and marks the title watched`() = runBlocking {
        val stream = playback.prepare(account, movieId, setOf("hevc"), 600_000)!!
        playback.reportStart(stream, 600_000)
        playback.reportProgress(stream, 700_000, paused = true)
        playback.reportStopped(stream, positionMs = 7_000_000, durationMs = 7_200_000)

        val paths = requests.map { it.first }
        assertTrue("/Sessions/Playing" in paths)
        assertTrue("/Sessions/Playing/Progress" in paths)
        val stop = Json.parseToJsonElement(requests.single { it.first == "/Sessions/Playing/Stopped" }.second).jsonObject
        assertEquals(70_000_000_000L, stop["PositionTicks"]!!.jsonPrimitive.long)
        assertEquals("session1", stop["PlaySessionId"]!!.jsonPrimitive.content)
        val item = database.jellyfinDao().item(account.id, movieId)!!
        assertTrue(item.played)
        assertEquals(0L, item.playbackPositionMs)
        assertNotNull(item.lastPlayedAt)
    }

    @Test
    fun `stopping part way keeps the resume point`() = runBlocking {
        val stream = playback.prepare(account, movieId, setOf("hevc"), 0)!!
        playback.reportStopped(stream, positionMs = 1_800_000, durationMs = 7_200_000)

        val item = database.jellyfinDao().item(account.id, movieId)!!
        assertFalse(item.played)
        assertEquals(1_800_000L, item.playbackPositionMs)
    }

    @Test
    fun `next episode follows season and number and skips specials`() = runBlocking {
        val show = id(50)
        fun episode(n: Int, season: Int, number: Int) = RemoteItemEntity(
            accountId = account.id, itemId = id(100 + n), type = "EPISODE", name = "E$n", seriesId = show, seasonNumber = season, episodeNumber = number
        )
        val episodes = listOf(episode(1, 1, 1), episode(2, 1, 2), episode(3, 2, 1), episode(4, 0, 1))
        database.jellyfinDao().upsertItems(episodes)

        assertEquals(episodes[1].itemId, playback.nextEpisode(account, episodes[0])?.itemId)
        assertEquals(episodes[2].itemId, playback.nextEpisode(account, episodes[1])?.itemId)
        assertNull(playback.nextEpisode(account, episodes[2]))
    }

    /** Adds the fields the SDK requires but these tests don't care about. */
    private fun complete(source: String): String {
        val streamDefaults = """"IsInterlaced":false,"IsOriginal":true,"IsDefault":false,"IsForced":false,"IsHearingImpaired":false,"IsExternal":false,"IsTextSubtitleStream":false,"SupportsExternalStream":false,"""
        val withStreams = source.replace("{\"Type\":", "{$streamDefaults\"Type\":")
        val sourceDefaults = """"Protocol":"Http","Type":"Default","IsRemote":false,"ReadAtNativeFramerate":false,"IgnoreDts":false,"IgnoreIndex":false,"GenPtsInput":false,"IsInfiniteStream":false,"RequiresOpening":false,"RequiresClosing":false,"RequiresLooping":false,"SupportsProbing":true,"TranscodingSubProtocol":"http","HasSegments":false,"""
        // Later keys win in kotlinx.serialization, so each test's own values still apply.
        return "{" + sourceDefaults + withStreams.trim().removePrefix("{")
    }

    private fun json(body: String) = MockResponse.Builder()
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun id(n: Int) = JellyfinIds.of(UUID(0, n.toLong()))
}
