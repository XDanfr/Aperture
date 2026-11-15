package me.xdan.aperture.ui.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Draws the engine's video. [resizeMode] is one of [VideoResizeModes]. */
@Composable
expect fun PlayerVideoSurface(
    output: Any?,
    useGLSurface: Boolean,
    resizeMode: Int,
    modifier: Modifier = Modifier
)

/** Draws [cues] in Aperture's subtitle style, ignoring styles embedded in the file. */
@Composable
expect fun PlayerSubtitles(cues: PlayerCues, style: PlayerSubtitleStyle, modifier: Modifier = Modifier)
