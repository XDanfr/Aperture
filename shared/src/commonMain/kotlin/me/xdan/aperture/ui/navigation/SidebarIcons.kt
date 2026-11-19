package me.xdan.aperture.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The icons a sidebar entry can wear (Settings › Sidebar). Ids are saved per profile, so they stay
 * stable when the set grows.
 */
object SidebarIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "search" to Icons.Rounded.Search,
        "movie" to Icons.Rounded.Movie,
        "tv" to Icons.Rounded.Tv,
        "list" to Icons.AutoMirrored.Rounded.PlaylistAdd,
        "explore" to Icons.Rounded.Explore,
        "download" to Icons.Rounded.Download,
        "videocam" to Icons.Rounded.Videocam,
        "video_library" to Icons.Rounded.VideoLibrary,
        "folder" to Icons.Rounded.Folder,
        "star" to Icons.Rounded.Star,
        "favorite" to Icons.Rounded.Favorite,
        "bookmark" to Icons.Rounded.Bookmark,
        "theaters" to Icons.Rounded.Theaters,
        "live_tv" to Icons.Rounded.LiveTv,
        "child" to Icons.Rounded.ChildCare,
        "family" to Icons.Rounded.FamilyRestroom,
        "animation" to Icons.Rounded.Animation,
        "music" to Icons.Rounded.MusicNote,
        "sports" to Icons.Rounded.SportsSoccer,
        "school" to Icons.Rounded.School,
        "nature" to Icons.Rounded.Park,
        "travel" to Icons.Rounded.Flight,
        "history" to Icons.Rounded.History,
        "new" to Icons.Rounded.NewReleases,
        "trending" to Icons.Rounded.TrendingUp,
        "camera" to Icons.Rounded.PhotoCamera,
        "game" to Icons.Rounded.SportsEsports,
        "home" to Icons.Rounded.Cottage,
        "celebration" to Icons.Rounded.Celebration,
        "nightlife" to Icons.Rounded.Nightlife,
        "science" to Icons.Rounded.Science,
        "public" to Icons.Rounded.Public
    )

    private val byId = all.toMap()

    fun byId(id: String?): ImageVector? = id?.let(byId::get)
}
