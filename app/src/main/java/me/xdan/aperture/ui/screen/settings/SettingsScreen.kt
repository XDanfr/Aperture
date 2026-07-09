package me.xdan.aperture.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uriHandler = LocalUriHandler.current
    var showLicenses by remember { mutableStateOf(false) }
    
    if (showLicenses) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(64.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(500.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(32.dp)) {
                    Text("Open Source Licenses", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(24.dp))
                    Text("• Nova Video Player (Archos)")
                    Text("• laposa/media-player")
                    Text("• ExoPlayer / Media3")
                    Text("• Jetpack Compose")
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { showLicenses = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
        
        item {
            SettingsItem(
                title = "Language",
                subtitle = "English (System Default)",
                icon = Icons.Rounded.Language,
                onClick = {}
            )
        }

        item {
            SettingsItem(
                title = "Force Rescan Local Files",
                subtitle = "Update library from storage",
                icon = Icons.Rounded.Refresh,
                onClick = { viewModel.forceRescan() }
            )
        }

        item {
            SettingsItem(
                title = "Subtitle Appearance",
                subtitle = "Size, Color, Opacity",
                icon = Icons.Rounded.Subtitles,
                onClick = {}
            )
        }

        item {
            SettingsItem(
                title = "Open Source Licenses",
                subtitle = "Nova Video Player, laposa, etc.",
                icon = Icons.Rounded.Info,
                onClick = { showLicenses = true }
            )
        }
        
        item {
            SettingsItem(
                title = "Clear Cache",
                subtitle = "Free up storage space",
                icon = Icons.Rounded.Storage,
                onClick = { viewModel.clearCache() }
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(64.dp))
            
            Button(
                onClick = { uriHandler.openUri("https://github.com/XDanfr") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Favorite, null)
                Spacer(Modifier.width(8.dp))
                Text("Donate to XDanfr on Github")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}
