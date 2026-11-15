package me.xdan.aperture.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack

@Composable
actual fun rememberDestinationBackStack(): NavBackStack<Destination> {
    @Suppress("UNCHECKED_CAST")
    return rememberNavBackStack(Destination.Home) as NavBackStack<Destination>
}
