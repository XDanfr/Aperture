package me.xdan.aperture.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

// Outside Android, Navigation 3 needs the destination types registered for saved state.
private val destinationConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Destination.Home::class)
            subclass(Destination.Search::class)
            subclass(Destination.Movies::class)
            subclass(Destination.Shows::class)
            subclass(Destination.MyList::class)
            subclass(Destination.Settings::class)
            subclass(Destination.Player::class)
        }
    }
}

@Composable
actual fun rememberDestinationBackStack(): NavBackStack<Destination> {
    @Suppress("UNCHECKED_CAST")
    return rememberNavBackStack(destinationConfiguration, Destination.Home) as NavBackStack<Destination>
}
