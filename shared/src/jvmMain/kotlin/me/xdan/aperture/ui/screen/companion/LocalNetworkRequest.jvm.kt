package me.xdan.aperture.ui.screen.companion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.xdan.aperture.companion.CompanionHub

// Desktop systems don't gate the local network behind a runtime permission.
@Composable
internal actual fun rememberLocalNetworkRequest(hub: CompanionHub): () -> Unit = remember(hub) { { hub.start() } }
