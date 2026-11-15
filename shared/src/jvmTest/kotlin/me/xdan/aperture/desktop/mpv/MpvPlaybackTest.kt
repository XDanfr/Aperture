package me.xdan.aperture.desktop.mpv

import me.xdan.aperture.ui.screen.player.ExternalSubtitle
import me.xdan.aperture.ui.screen.player.PlaybackStates
import me.xdan.aperture.ui.screen.player.TrackTypes
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Plays a generated clip through the real libmpv. Skipped when libmpv or ffmpeg isn't installed,
 * so CI without mpv still passes. Rendered frames are written to build/mpv-test for a look.
 */
class MpvPlaybackTest {
    private val outDir = File("build/mpv-test").apply { mkdirs() }
    private lateinit var video: File
    private lateinit var subtitle: File
    private var engine: MpvPlayerEngine? = null

    @Before
    fun setUp() {
        assumeTrue("libmpv not installed", LibMpv.instance != null)
        assumeTrue("ffmpeg not installed", runCatching { run("ffmpeg", "-version") }.getOrDefault(false))
        video = File(outDir, "clip.mkv")
        subtitle = File(outDir, "clip.srt").apply {
            writeText("1\n00:00:00,500 --> 00:00:03,500\nHello from Aperture\n\n")
        }
        check(
            run(
                "ffmpeg", "-y", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=25:duration=6",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=6",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", video.path
            )
        ) { "ffmpeg couldn't make the test clip" }
    }

    @After
    fun tearDown() {
        engine?.release()
    }

    @Test
    fun playsSeeksRendersAndEnds() {
        val player = MpvPlayerEngine().also { engine = it }
        player.setMedia(
            video.absolutePath,
            listOf(ExternalSubtitle(subtitle.absolutePath, "application/x-subrip", "English", "en", isDefault = true))
        )
        player.play()

        waitFor("playing") { player.isPlaying.value && player.playbackState.value == PlaybackStates.READY }
        assertTrue(player.duration in 5_800..6_200, "duration ${player.duration}")

        waitFor("tracks") { player.tracks.value.groups.map { it.type }.containsAll(listOf(TrackTypes.VIDEO, TrackTypes.AUDIO, TrackTypes.TEXT)) }
        waitFor("external subtitle selected") {
            player.tracks.value.groups.first { it.type == TrackTypes.TEXT }.tracks.single().isSelected
        }
        println("Decoders: ${player.videoDecoderName.value} / ${player.audioDecoderName.value}")

        waitFor("subtitle cue") { player.cues.value.text.contains("Hello from Aperture") }

        val output = assertNotNull(player.output)
        output.setTargetSize(640, 360)
        val firstFrame = output.frames.value
        waitFor("frame") { output.frames.value > firstFrame }
        val playing = snapshot(output, "playing.png")
        assertTrue(colourfulPixels(playing) > 1_000, "rendered frame has picture")

        val before = player.currentPosition
        waitFor("position advances") { player.currentPosition > before + 300 }

        player.seekTo(4_000)
        waitFor("seek") { player.currentPosition in 4_000..5_500 }
        player.pause()
        waitFor("paused") { !player.isPlaying.value }
        player.play()
        waitFor("ended", timeoutMs = 10_000) { player.playbackState.value == PlaybackStates.ENDED }
    }

    @Test
    fun grabsPreviewFrames() {
        val grabber = assertNotNull(MpvFrameGrabber.create())
        try {
            val frame = assertNotNull(grabber.grab(video.absolutePath, 2_000, 320, 180))
            assertEquals(320, frame.width)
            File(outDir, "preview.png").writeBytes(assertNotNull(frame.encodeToData(EncodedImageFormat.PNG)).bytes)
            assertTrue(colourfulPixels(frame) > 500, "preview frame has picture")
        } finally {
            grabber.release()
        }
    }

    private fun snapshot(output: MpvVideoOutput, name: String): Image {
        val surface = Surface.makeRasterN32Premul(640, 360)
        output.draw(surface.canvas, Rect.makeWH(640f, 360f))
        val image = surface.makeImageSnapshot()
        File(outDir, name).writeBytes(assertNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
        return image
    }

    /** Pixels that aren't near-black or grey: testsrc2 is full of saturated colour. */
    private fun colourfulPixels(image: Image): Int {
        val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
        var count = 0
        for (y in 0 until image.height step 4) for (x in 0 until image.width step 4) {
            val c = bitmap.getColor(x, y)
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (maxOf(r, g, b) - minOf(r, g, b) > 60) count++
        }
        return count
    }

    private fun waitFor(what: String, timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for $what" }
            Thread.sleep(20)
        }
    }

    private fun run(vararg command: String): Boolean {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().readText()
        return process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0
    }
}
