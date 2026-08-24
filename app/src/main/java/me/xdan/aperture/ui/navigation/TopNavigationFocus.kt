package me.xdan.aperture.ui.navigation

/**
 * NavContent is intentionally kept as a separate composable from NavGraph.
 * The top navigation owns focus while Home is entered from that navigation,
 * so its content entry requester should not pull focus away from the bar.
 */
internal const val topNavigationHasFocus: Boolean = true
