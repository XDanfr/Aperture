package me.xdan.aperture.ui.navigation

import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
sealed interface Destination : NavKey {
    @Serializable
    data object Home : Destination

    @Serializable
    data object Search : Destination

    @Serializable
    data object Movies : Destination

    @Serializable
    data object Shows : Destination

    @Serializable
    data object MyList : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data class Player(
        val media: me.xdan.aperture.domain.model.MediaKey,
        val startFromBeginning: Boolean = false
    ) : Destination
}

/** The app's back stack, saved across configuration changes (and, on desktop, by serializer). */
@androidx.compose.runtime.Composable
expect fun rememberDestinationBackStack(): androidx.navigation3.runtime.NavBackStack<Destination>
