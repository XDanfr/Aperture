package me.xdan.aperture.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.domain.model.AmbientBrandPlacement
import me.xdan.aperture.domain.model.AmbientModeType
import me.xdan.aperture.domain.model.AmbientSettings

/** A static layout sketch; the preview does not load artwork or run Ambient animations. */
@Composable
internal fun AmbientSettingsPreview(settings: AmbientSettings, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val backdrop = MaterialTheme.colorScheme.background
    val caption = (if (settings.mode == AmbientModeType.CINEMATIC) "Cinematic" else "Poster wall") +
        if (settings.showClock) ", clock shown" else ", clock hidden"
    Box(
        modifier.aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(backdrop)
            .border(1.dp, outline, RoundedCornerShape(12.dp))
            .clearAndSetSemantics { contentDescription = caption }
    ) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val stroke = Stroke(1.dp.toPx())
            if (settings.mode == AmbientModeType.POSTER_WALL) {
                val gap = 5.dp.toPx()
                val cellWidth = (size.width - gap * 5) / 6
                val cellHeight = (size.height - gap * 2) / 3
                repeat(3) { row ->
                    repeat(6) { column ->
                        drawRoundRect(
                            outline,
                            topLeft = Offset(column * (cellWidth + gap), row * (cellHeight + gap)),
                            size = Size(cellWidth, cellHeight),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                            style = stroke
                        )
                    }
                }
            } else {
                drawPath(
                    Path().apply {
                        moveTo(0f, size.height * 0.8f)
                        lineTo(size.width * 0.3f, size.height * 0.3f)
                        lineTo(size.width * 0.55f, size.height * 0.65f)
                        lineTo(size.width * 0.75f, size.height * 0.4f)
                        lineTo(size.width, size.height * 0.8f)
                    }, outline, style = stroke
                )
                drawLine(outline, Offset(0f, size.height * 0.9f), Offset(size.width * 0.4f, size.height * 0.9f), stroke.width)
            }
        }
        if (settings.mode == AmbientModeType.POSTER_WALL && settings.wallBrandPlacement == AmbientBrandPlacement.CENTRE) {
            Column(
                Modifier.align(Alignment.Center).background(backdrop.copy(alpha = 0.9f), RoundedCornerShape(12.dp)).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ApertureBrandMark(Modifier.size(32.dp))
                if (settings.showClock) Text("20:45", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Row(
                Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(backdrop.copy(alpha = 0.9f), RoundedCornerShape(8.dp)).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ApertureBrandMark(Modifier.size(16.dp))
                Text("Aperture", style = MaterialTheme.typography.labelSmall)
                if (settings.showClock) Text("20:45", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
