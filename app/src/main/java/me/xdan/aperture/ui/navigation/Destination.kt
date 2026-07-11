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
        val mediaId: Long,
        val startFromBeginning: Boolean = false
    ) : Destination
}
