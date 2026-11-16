package me.xdan.aperture.desktop.mpv

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.xdan.aperture.companion.CompanionPlatform
import me.xdan.aperture.data.jellyfin.JellyfinAccounts
import me.xdan.aperture.data.jellyfin.JellyfinClients
import me.xdan.aperture.data.jellyfin.JellyfinCredentials
import me.xdan.aperture.data.jellyfin.JellyfinPlayback
import me.xdan.aperture.data.jellyfin.JellyfinSync
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.data.local.entity.ProfileEntity
import me.xdan.aperture.data.repository.ProfileRepositoryImpl
import me.xdan.aperture.data.repository.UserPreferencesRepositoryImpl
import me.xdan.aperture.platform.PropertiesStores
import me.xdan.aperture.ui.screen.player.ExternalSubtitle
import me.xdan.aperture.ui.screen.player.PlaybackStates
import me.xdan.aperture.ui.screen.player.TrackTypes
import okio.Path.Companion.toOkioPath
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertTrue

/**
 * Streams a few titles from a Jellyfin server through the real libmpv: direct play, a forced
 * transcode, and server subtitles. Skipped unless APERTURE_JELLYFIN_URL/USER/PASSWORD are set and
 * libmpv is installed. Playback is only reported to the server with APERTURE_JELLYFIN_REPORT=1,
 * so running it against a real library doesn't touch anyone's watch history.
 */
class JellyfinStreamLiveTest {
    private val url = System.getenv("APERTURE_JELLYFIN_URL")
    private val user = System.getenv("APERTURE_JELLYFIN_USER")
    private val password = System.getenv("APERTURE_JELLYFIN_PASSWORD")
    private val report = System.getenv("APERTURE_JELLYFIN_REPORT") == "1"
    private val outDir = File("build/mpv-test").apply { mkdirs() }
    /** The desktop platform's codecs; mpv decodes all of these. */
    private val codecs = setOf("h264", "hevc", "vp8", "vp9", "av1", "mpeg4", "mpeg2video", "vc1")

    @Test
    fun `streams server titles through mpv`() = runBlocking<Unit> {
        assumeTrue("Set APERTURE_JELLYFIN_* to run against a server", !url.isNullOrBlank() && !user.isNullOrBlank())
        assumeTrue("libmpv not installed", LibMpv.instance != null)
        val temp = Files.createTempDirectory("jellyfin-stream").toFile()
        val database = Room.databaseBuilder<AppDatabase>(File(temp, "db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        val player = MpvPlayerEngine()
        try {
            val credentials = JellyfinCredentials(PropertiesStores(File(temp, "prefs")))
            val clients = JellyfinClients(
                createJellyfin { clientInfo = ClientInfo("Aperture", "stream-test") },
                credentials,
                object : CompanionPlatform {
                    override val hasLocalNetworkPermission = true
                    override val deviceName = "Aperture stream test"
                    override fun localIpv4Address(): String? = null
                    override fun advertise(serviceName: String, port: Int, txt: Map<String, String>) = Unit
                    override fun stopAdvertising() = Unit
                }
            )
            val preferences = UserPreferencesRepositoryImpl(
                PreferenceDataStoreFactory.createWithPath { File(temp, "prefs.preferences_pb").toOkioPath() }
            )
            val profiles = ProfileRepositoryImpl(database.profileDao(), preferences, database.jellyfinDao(), credentials)
            profiles.profiles.first()
            val accounts = JellyfinAccounts(database.jellyfinDao(), clients, credentials, profiles)
            val account = accounts.signInWithPassword(ProfileEntity.DEFAULT_ID, url!!, user!!, password.orEmpty())
            JellyfinSync(database.jellyfinDao(), clients).fullSync(account)
            val playback = JellyfinPlayback(clients, database.jellyfinDao(), MutableStateFlow(account))

            val dao = database.jellyfinDao()
            val movies = dao.itemsOfType(account.id, "MOVIE").first().sortedBy { it.sortName ?: it.name }.take(3)
            val episode = dao.itemsOfType(account.id, "EPISODE").first().firstOrNull()
            val titles = movies + listOfNotNull(episode)
            assertTrue(titles.isNotEmpty(), "the server has something to play")

            for (item in titles) {
                for (allowDirect in listOf(true, false)) {
                    val label = "${item.name} (${if (allowDirect) "direct allowed" else "forced transcode"})"
                    val stream = playback.prepare(account, item.itemId, codecs, startPositionMs = 0, allowDirect = allowDirect)
                    if (stream == null) {
                        println("$label: server offered nothing to play")
                        continue
                    }
                    if (!allowDirect) assertTrue(stream.method == PlayMethod.TRANSCODE, "$label asked for a transcode")
                    player.setMedia(stream.url, stream.subtitles.map { ExternalSubtitle(it.url, it.mimeType, it.label, it.language, it.isDefault) })
                    player.play()
                    waitFor("$label playing", 30_000) { player.isPlaying.value && player.playbackState.value == PlaybackStates.READY }
                    if (report) playback.reportStart(stream, 0)
                    val start = player.currentPosition
                    waitFor("$label position advances", 20_000) { player.currentPosition > start + 1_500 }

                    val output = player.output
                    if (output != null) {
                        output.setTargetSize(640, 360)
                        val first = output.frames.value
                        waitFor("$label frame", 10_000) { output.frames.value > first }
                    }
                    val textTracks = player.tracks.value.groups.filter { it.type == TrackTypes.TEXT }.flatMap { it.tracks }
                    if (stream.subtitles.isNotEmpty()) {
                        waitFor("$label server subtitles loaded", 10_000) {
                            player.tracks.value.groups.any { it.type == TrackTypes.TEXT }
                        }
                    }
                    if (report) playback.reportProgress(stream, player.currentPosition, paused = false)
                    println(
                        "$label: ${stream.method}, ${player.duration / 1000}s long, decoders ${player.videoDecoderName.value} / " +
                            "${player.audioDecoderName.value}, ${stream.subtitles.size} server subtitle(s), " +
                            "${textTracks.size} text track(s), reached ${player.currentPosition} ms"
                    )
                    if (report) playback.reportStopped(stream, player.currentPosition, player.duration)
                    player.stop()
                }
            }
            accounts.signOut(ProfileEntity.DEFAULT_ID)
        } finally {
            player.release()
            database.close()
            temp.deleteRecursively()
        }
    }

    private fun waitFor(what: String, timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for $what" }
            Thread.sleep(50)
        }
    }
}
