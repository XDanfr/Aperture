package me.xdan.aperture.ui.screen.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import me.xdan.aperture.desktop.mpv.MpvVideoOutput
import org.jetbrains.skia.Rect

/** The text of the subtitle on screen (mpv's `sub-text`), drawn in Aperture's subtitle style. */
actual class PlayerCues(val text: String)

@Composable
actual fun PlayerVideoSurface(output: Any?, useGLSurface: Boolean, resizeMode: Int, modifier: Modifier) {
    val video = output as? MpvVideoOutput
    LaunchedEffect(video, resizeMode) { video?.setResizeMode(resizeMode) }
    if (video == null) {
        Box(modifier)
        return
    }
    // Reading the frame counter in the draw phase redraws per frame without recomposing.
    val frames = video.frames.collectAsState()
    Canvas(modifier.onSizeChanged { video.setTargetSize(it.width, it.height) }) {
        frames.value
        drawIntoCanvas { canvas ->
            video.draw(canvas.nativeCanvas, Rect.makeWH(size.width, size.height))
        }
    }
}

/**
 * Mirrors Media3's SubtitleView as Aperture configures it: text at 5.33% of the view height times
 * the user's scale, bottom-aligned above a 8% margin, a drop shadow edge and a per-line background.
 */
@Composable
actual fun PlayerSubtitles(cues: PlayerCues, style: PlayerSubtitleStyle, modifier: Modifier) {
    if (cues.text.isBlank()) return
    val textColour = when (style.colour) {
        "yellow" -> Color.Yellow
        "cyan" -> Color.Cyan
        else -> Color.White
    }
    val backgroundColour = Color(12, 12, 14, alpha = (style.backgroundOpacity.coerceIn(0f, 0.9f) * 255).toInt())
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val textSize = with(density) { (maxHeight.toPx() * 0.0533f * style.textScale).toSp() }
        BasicText(
            text = AnnotatedString(cues.text.trim(), SpanStyle(background = backgroundColour)),
            style = TextStyle(
                color = textColour,
                fontSize = textSize,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                shadow = Shadow(Color.Black, offset = Offset(2f, 2f), blurRadius = 4f)
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = maxHeight * 0.08f)
        )
    }
}
