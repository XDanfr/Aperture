package me.xdan.aperture.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.ui.component.expressive.ExpressiveLoadingIndicator
import me.xdan.aperture.ui.component.expressive.ExpressiveProgressIndicator
import me.xdan.aperture.ui.component.expressive.ExpressiveSlider
import me.xdan.aperture.ui.component.expressive.ExpressiveToggle

@Preview(device = "id:tv_1080p")
@Composable
fun ExpressiveComponentPreview() {
    ApertureTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ApertureTheme.colorScheme.background)
                .padding(ApertureTheme.spacing.huge),
            verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large)
        ) {
            Text(
                "Material 3 Expressive Components",
                style = MaterialTheme.typography.headlineLarge,
                color = ApertureTheme.colorScheme.onBackground
            )

            Row(horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.huge)) {
                // Toggles
                Column(verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)) {
                    Text("Toggles", style = MaterialTheme.typography.titleMedium)
                    var checked1 by remember { mutableStateOf(true) }
                    var checked2 by remember { mutableStateOf(false) }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ExpressiveToggle(checked = checked1, onCheckedChange = { checked1 = it }, isFocused = true)
                        Text("Focused On")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ExpressiveToggle(checked = checked2, onCheckedChange = { checked2 = it })
                        Text("Off")
                    }
                }

                // Loading
                Column(verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)) {
                    Text("Loading", style = MaterialTheme.typography.titleMedium)
                    ExpressiveLoadingIndicator()
                }
            }

            // Slider
            Column(verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)) {
                Text("Slider (Wavy on Focus)", style = MaterialTheme.typography.titleMedium)
                var sliderValue by remember { mutableStateOf(0.5f) }
                ExpressiveSlider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    isFocused = true
                )
                ExpressiveSlider(
                    value = 0.3f,
                    onValueChange = {},
                    isFocused = false
                )
            }

            // Progress
            Column(verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)) {
                Text("Progress (Pop on Increase)", style = MaterialTheme.typography.titleMedium)
                var progressValue by remember { mutableStateOf(0.2f) }
                ExpressiveProgressIndicator(progress = progressValue)
                Button(onClick = { progressValue = (progressValue + 0.1f).coerceAtMost(1f) }) {
                    Text("Increase Progress")
                }
            }
        }
    }
}
