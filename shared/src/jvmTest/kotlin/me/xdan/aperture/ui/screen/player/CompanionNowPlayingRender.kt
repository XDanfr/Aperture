package me.xdan.aperture.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.xdan.aperture.ui.component.SplitWaveProgress
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * A mock of the companion's Now Playing sheet and mini bar (the real ones are Android-only), with
 * the same components: set APERTURE_RENDER_DIR to run.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
class CompanionNowPlayingRender {
    private val splits = listOf(45f, 70f, 160f, 1230f).map { it / 1320f }

    @Test
    fun render() {
        val dir = System.getenv("APERTURE_RENDER_DIR") ?: return
        val scene = ImageComposeScene(1080, 2340, Density(2.625f)) {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72), secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8), surfaceContainerLow = Color(0xFF1D1B20), surfaceContainerHigh = Color(0xFF2B2930))) {
                Box(Modifier.fillMaxSize().background(Color(0xFF141218))) {
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                        Sheet()
                    }
                    MiniBar(Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
                }
            }
        }
        repeat(40) { scene.render(it * 33_000_000L) }
        File(dir, "companion-now-playing.png").writeBytes(scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    @Composable
    private fun MiniBar(modifier: Modifier) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge, modifier = modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            Column {
                Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp, 60.dp).clip(MaterialTheme.shapes.small).background(Color(0xFF6A5A8A)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Example Show", style = MaterialTheme.typography.titleSmall)
                        Text("S1 · E3 · The Episode", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {}) { Icon(Icons.Rounded.Replay10, null) }
                    FilledIconButton(onClick = {}) { Icon(Icons.Rounded.Pause, null) }
                }
                SplitWaveProgress(
                    progress = { 0.09f }, wavy = true,
                    indicatorColor = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    splits = splits,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp, top = 2.dp)
                )
            }
        }
    }

    @Composable
    private fun Sheet() {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(32.dp, 4.dp).clip(CircleShapeLike).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF3A2A4A), Color(0xFF1B2735)))))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Playing on your TV", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Example Show", style = MaterialTheme.typography.headlineSmall)
                    Text("S1 · E3 · The Episode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    Slider(value = 0.09f, onValueChange = {}, track = { state ->
                        SliderDefaults.Track(sliderState = state, modifier = Modifier.splitGaps(splits))
                    })
                    Row(Modifier.fillMaxWidth()) { Text("1:58", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.weight(1f)); Text("22:00", style = MaterialTheme.typography.labelMedium) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    FilledTonalIconButton(onClick = {}, modifier = Modifier.size(IconButtonDefaults.mediumContainerSize())) { Icon(Icons.Rounded.Replay10, null) }
                    FilledIconButton(onClick = {}, modifier = Modifier.size(IconButtonDefaults.largeContainerSize())) { Icon(Icons.Rounded.Pause, null, Modifier.size(IconButtonDefaults.largeIconSize)) }
                    FilledTonalIconButton(onClick = {}, modifier = Modifier.size(IconButtonDefaults.mediumContainerSize())) { Icon(Icons.Rounded.Forward10, null) }
                }
                Button(onClick = {}, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) { Icon(Icons.Rounded.SkipNext, null); Spacer(Modifier.width(8.dp)); Text("Skip Intro", style = MaterialTheme.typography.titleMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {}) { Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stop") }
                    OutlinedButton(onClick = {}) { Icon(Icons.Rounded.SkipNext, null); Spacer(Modifier.width(6.dp)); Text("Next episode") }
                }
            }
        }
    }
}

private val CircleShapeLike = RoundedCornerShape(50)

private fun Modifier.splitGaps(splits: List<Float>): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val gap = 6.dp.toPx()
        splits.forEach { split ->
            drawRect(Color.Black, topLeft = Offset(size.width * split - gap / 2f, 0f), size = Size(gap, size.height), blendMode = BlendMode.Clear)
        }
    }
