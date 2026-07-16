package me.xdan.aperture.ui.screen.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

internal const val TMDB_URL = "https://www.themoviedb.org"
internal const val GITHUB_SPONSORS_URL = "https://github.com/sponsors/XDanfr"

@Composable
internal fun LinkQrDialog(
    title: String,
    description: String,
    url: String,
    qrRows: List<String>,
    onDismiss: () -> Unit
) {
    val closeRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { closeRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.width(760.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(34.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemedQrCode(rows = qrRows)
                Column(
                    modifier = Modifier.width(400.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                    )
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End).focusRequester(closeRequester)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemedQrCode(rows: List<String>) {
    val accent = MaterialTheme.colorScheme.primary
    // Keep the code conventionally dark-on-light for reliable phone scanning,
    // while retaining the selected theme's hue in both its modules and frame.
    val moduleColour = androidx.compose.ui.graphics.Color(
        red = accent.red * 0.32f,
        green = accent.green * 0.32f,
        blue = accent.blue * 0.32f,
        alpha = 1f
    )
    Surface(
        modifier = Modifier.size(260.dp),
        shape = RoundedCornerShape(24.dp),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.White
                )
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val quietZone = 4
                    val totalModules = rows.size + quietZone * 2
                    val moduleSize = size.minDimension / totalModules
                    val startX = (size.width - moduleSize * totalModules) / 2f
                    val startY = (size.height - moduleSize * totalModules) / 2f
                    rows.forEachIndexed { y, row ->
                        row.forEachIndexed { x, value ->
                            if (value == '1') {
                                drawRoundRect(
                                    color = moduleColour,
                                    topLeft = Offset(
                                        startX + (x + quietZone) * moduleSize,
                                        startY + (y + quietZone) * moduleSize
                                    ),
                                    size = Size(moduleSize, moduleSize),
                                    cornerRadius = CornerRadius(moduleSize * 0.22f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal val TMDB_QR_ROWS = listOf(
    "111111100010011101100111101111111",
    "100000100011111011010000001000001",
    "101110101100001010011101001011101",
    "101110101111010100000101001011101",
    "101110101100010001100011101011101",
    "100000100110111000111100101000001",
    "111111101010101010101010101111111",
    "000000000011101011010111100000000",
    "111001111010001010111000110011100",
    "101101000110111110011010010111011",
    "100010110100000101001101000101011",
    "001011011011110000101110101010101",
    "111110100000100011011010001111111",
    "111110000101101111000001000100011",
    "000000110110011110101000000011010",
    "011110001000001010010001000101101",
    "110000110111000111011110001111010",
    "100101001110010011010110001011010",
    "010101101101010110111001010001100",
    "010000011100110011100000111011100",
    "101100100010001001011010100010100",
    "111000010000100100110101011110001",
    "011101111110101100111000100101101",
    "101100000101110011010011101000001",
    "111010111010101101011010111110101",
    "000000001110111010010100100011110",
    "111111101111010001000000101010100",
    "100000101010101010001100100010010",
    "101110100000011110001010111111000",
    "101110100101010100011111101100101",
    "101110101101000010110101001101111",
    "100000101111101000011111111111001",
    "111111101101010110010101110110000"
)

internal val GITHUB_SPONSORS_QR_ROWS = listOf(
    "111111100100100110011010101111111",
    "100000100000101100011011001000001",
    "101110100110010000011100101011101",
    "101110101100100111111011101011101",
    "101110100101101100100100101011101",
    "100000100111111100010101001000001",
    "111111101010101010101010101111111",
    "000000000000001011000111100000000",
    "110111100110000110110110010001000",
    "100111011011101001011001010110101",
    "010011100100100110011010111011011",
    "000111000111100001111011111001010",
    "011000100110010111000100000100010",
    "100110000101000000001111010001011",
    "001100111011000011100100110001111",
    "001011001010001100111100001011010",
    "100011111000000010011111100111011",
    "101000011010111111000101010110111",
    "111110101101100010111101000100001",
    "100110000011001000101101001110010",
    "000000101111010111001111100111010",
    "100000011111101111111011111101111",
    "101100100010000100100011001000010",
    "110010000001101001001000010001110",
    "101000110111101000001100111111011",
    "000000000011110101101111100010111",
    "111111100101110010100111101011000",
    "100000101000000110110000100010111",
    "101110101000111110010000111111111",
    "101110101010010100000111100011100",
    "101110100110000001100110010001001",
    "100000101100101110100100101001001",
    "111111101110110110110110010100100"
)
