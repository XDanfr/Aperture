package me.xdan.aperture.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.ui.theme.ApertureTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Renders a mock player frame with the Skip button; set APERTURE_RENDER_DIR to run. */
class SkipButtonRender {
    @Test
    fun render() {
        val dir = System.getenv("APERTURE_RENDER_DIR") ?: return
        listOf(false, true).forEach { focused -> frame(File(dir, if (focused) "skip-focused.png" else "skip-osd.png"), focused) }
    }

    private fun frame(out: File, focused: Boolean) {
        val splits = listOf(45f, 70f, 160f, 1230f).map { it / 1320f }
        val scene = ImageComposeScene(1920, 1080) {
            ApertureTheme {
                val requester = remember { FocusRequester() }
                if (focused) LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B2735), Color(0xFF3A2A4A), Color(0xFF0E0E12))))) {
                    Text("(video)", color = Color.White.copy(alpha = 0.25f), style = MaterialTheme.typography.displayMedium, modifier = Modifier.align(Alignment.Center))
                    if (!focused) {
                        // The controls showing: title and the split bar along the bottom.
                        Column(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                                .padding(horizontal = 48.dp, vertical = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Example Show", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                            Text("S1 · E3 · The Episode", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
                            PlaybackWaveProgress(progress = { 0.09f }, wavy = true, modifier = Modifier.fillMaxWidth(), splits = splits)
                            Row { Text("1:58", color = Color.White); Spacer(Modifier.weight(1f)); Text("22:00", color = Color.White) }
                        }
                    }
                    Box(Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = if (focused) 48.dp else 160.dp)) {
                        SkipSegmentButton(label = "Skip Intro", focusRequester = requester, onSkip = {}, previewFocused = focused)
                    }
                }
            }
        }
        repeat(40) { scene.render(it * 33_000_000L) }
        out.writeBytes(scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }
}
