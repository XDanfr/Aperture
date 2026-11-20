package me.xdan.aperture.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.ui.theme.ApertureTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Renders the split bar to a PNG for a look; set APERTURE_RENDER_OUT to run. */
class SplitProgressRender {
    @Test
    fun render() {
        val out = System.getenv("APERTURE_RENDER_OUT") ?: return
        // A 22-min episode: recap 0:00–0:45, intro 1:10–2:40, credits 20:30–22:00.
        val splits = listOf(45f, 70f, 160f, 1230f).map { it / 1320f }
        val scene = ImageComposeScene(1600, 520) {
            ApertureTheme {
                Column(Modifier.fillMaxSize().background(Color.Black).padding(40.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    listOf(
                        Triple("Playing, in the intro", 0.09f, true),
                        Triple("Playing, in the episode", 0.5f, true),
                        Triple("Paused", 0.5f, false),
                        Triple("Playing, in the credits", 0.97f, true)
                    ).forEach { (label, p, wavy) ->
                        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                        PlaybackWaveProgress(progress = { p }, wavy = wavy, modifier = Modifier.fillMaxWidth(), splits = splits)
                    }
                }
            }
        }
        repeat(30) { scene.render(it * 33_000_000L) }
        val image = scene.render(1_000_000_000L)
        File(out).writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        // The scrubbing preview card in chapter mode (Up pressed while scrubbing).
        val chapters = buildChapters(listOf(
            me.xdan.aperture.data.jellyfin.JellyfinPlayback.Segment(me.xdan.aperture.data.jellyfin.JellyfinPlayback.SegmentKind.RECAP, 0, 45_000),
            me.xdan.aperture.data.jellyfin.JellyfinPlayback.Segment(me.xdan.aperture.data.jellyfin.JellyfinPlayback.SegmentKind.INTRO, 70_000, 160_000),
            me.xdan.aperture.data.jellyfin.JellyfinPlayback.Segment(me.xdan.aperture.data.jellyfin.JellyfinPlayback.SegmentKind.OUTRO, 1_230_000, 1_320_000)
        ), 1_320_000)
        val card = ImageComposeScene(1000, 330) {
            ApertureTheme {
                Row(Modifier.fillMaxSize().background(Color.Black).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    listOf(false, true).forEach { mode ->
                        androidx.tv.material3.Surface(modifier = Modifier.width(320.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), colors = androidx.tv.material3.SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column {
                                Box(Modifier.fillMaxWidth().height(180.dp).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF3A2A4A), Color(0xFF1B2735)))))
                                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    ChapterPreviewLabel(chapters, 70_000, chapterMode = mode)
                                    Text("1:10", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
        repeat(10) { card.render(it * 33_000_000L) }
        File(File(out).parentFile, "tv-chapter-preview.png").writeBytes(card.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        card.close()
    }
}
