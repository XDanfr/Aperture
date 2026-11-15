package me.xdan.aperture.mobile.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import me.xdan.aperture.companion.protocol.DeviceKind

/** The icon for the device running Aperture: a TV, or a laptop or desktop for the desktop app. */
fun deviceIcon(kind: String?): ImageVector = when (kind) {
    DeviceKind.LAPTOP -> Icons.Rounded.Laptop
    DeviceKind.DESKTOP -> Icons.Rounded.DesktopWindows
    else -> Icons.Rounded.Tv
}
