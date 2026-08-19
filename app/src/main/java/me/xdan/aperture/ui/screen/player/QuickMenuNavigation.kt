package me.xdan.aperture.ui.screen.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoSettings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation state for the player Quick Menu.
 *
 * Pages intentionally form a stack rather than a horizontal carousel so TV
 * navigation remains predictable when a page later opens deeper configuration.
 */
sealed interface QuickMenuPage {
    data object Categories : QuickMenuPage
    data object Audio : QuickMenuPage
    data object Subtitles : QuickMenuPage
    data object Playback : QuickMenuPage
    data object Video : QuickMenuPage
    data object Other : QuickMenuPage
}

data class QuickMenuCategory(
    val page: QuickMenuPage,
    val title: String,
    val icon: ImageVector
)

val quickMenuCategories = listOf(
    QuickMenuCategory(
        page = QuickMenuPage.Audio,
        title = "Audio",
        icon = Icons.Rounded.Audiotrack
    ),
    QuickMenuCategory(
        page = QuickMenuPage.Subtitles,
        title = "Subtitles",
        icon = Icons.Rounded.Subtitles
    ),
    QuickMenuCategory(
        page = QuickMenuPage.Playback,
        title = "Playback",
        icon = Icons.Rounded.Tune
    ),
    QuickMenuCategory(
        page = QuickMenuPage.Video,
        title = "Video",
        icon = Icons.Rounded.VideoSettings
    ),
    QuickMenuCategory(
        page = QuickMenuPage.Other,
        title = "Other",
        icon = Icons.Rounded.Settings
    )
)
