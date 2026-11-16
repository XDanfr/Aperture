package me.xdan.aperture.ui.platform

import androidx.compose.runtime.Composable

/**
 * Asks for access to devices on the local network (Android 17's ACCESS_LOCAL_NETWORK), which both
 * the phone companion and a Jellyfin server on the LAN need. Platforms without the permission
 * report it granted straight away.
 */
@Composable
expect fun rememberLocalNetworkPermission(onResult: (granted: Boolean) -> Unit): () -> Unit
