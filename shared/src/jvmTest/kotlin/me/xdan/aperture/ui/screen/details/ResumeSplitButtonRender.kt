package me.xdan.aperture.ui.screen.details

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import me.xdan.aperture.ui.theme.ApertureTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Renders the details action row with the Continue/Restart split button; set APERTURE_RENDER_DIR to run. */
class ResumeSplitButtonRender {
    @Test
    fun render() {
        val dir = System.getenv("APERTURE_RENDER_DIR") ?: return
        listOf("none", "continue", "restart").forEach { frame(File(dir, "split-$it.png"), it) }
    }

    private fun frame(out: File, focus: String) {
        val scene = ImageComposeScene(1100, 300) {
            ApertureTheme {
                val cont = remember { FocusRequester() }
                val restart = remember { FocusRequester() }
                val contSource = remember { MutableInteractionSource() }
                val restartSource = remember { MutableInteractionSource() }
                LaunchedEffect(Unit) {
                    repeat(3) { androidx.compose.runtime.withFrameNanos { } }
                    when (focus) {
                        "continue" -> contSource.emit(FocusInteraction.Focus())
                        "restart" -> restartSource.emit(FocusInteraction.Focus())
                    }
                }
                val outer = 26.dp
                val inner = 4.dp
                Column(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B2735), Color(0xFF2A2036))))
                        .padding(40.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Example Show", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text("S1 · E3 · 12 min left", color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Button(
                                onClick = {},
                                modifier = Modifier.height(SplitButtonHeight).focusRequester(cont),
                                interactionSource = contSource,
                                shape = ButtonDefaults.shape(
                                    shape = RoundedCornerShape(outer, inner, inner, outer),
                                    focusedShape = RoundedCornerShape(outer),
                                    pressedShape = RoundedCornerShape(outer)
                                )
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Continue")
                            }
                            Button(
                                onClick = {},
                                modifier = Modifier.size(SplitButtonHeight).focusRequester(restart),
                                interactionSource = restartSource,
                                shape = ButtonDefaults.shape(
                                    shape = RoundedCornerShape(inner, outer, outer, inner),
                                    focusedShape = CircleShape,
                                    pressedShape = CircleShape
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) { Icon(Icons.Rounded.Replay, contentDescription = "Restart", modifier = Modifier.requiredSize(24.dp)) }
                        }
                        OutlinedButton(onClick = {}) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null); Spacer(Modifier.width(8.dp)); Text("My List")
                        }
                        IconButton(onClick = {}) { Icon(Icons.Rounded.RadioButtonUnchecked, null) }
                        IconButton(onClick = {}) { Icon(Icons.Rounded.ImageSearch, null) }
                        IconButton(onClick = {}) { Icon(Icons.Rounded.Info, null) }
                    }
                }
            }
        }
        repeat(40) { scene.render(it * 33_000_000L) }
        out.writeBytes(scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }
}
