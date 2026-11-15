@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import me.xdan.aperture.platform.Log

actual typealias PlayerCues = CueGroup

@Composable
actual fun PlayerVideoSurface(output: Any?, useGLSurface: Boolean, resizeMode: Int, modifier: Modifier) {
    val nativePlayer = output as? Player
    key(useGLSurface) {
        AndroidView(
            factory = { context ->
                val themedContext = if (useGLSurface) {
                    ContextThemeWrapper(context, me.xdan.aperture.shared.R.style.PlayerViewGL)
                } else {
                    context
                }
                Log.d("PlayerScreen", "Creating PlayerView, initial nativePlayer: ${nativePlayer != null}, useGLSurface: $useGLSurface")
                PlayerView(themedContext).apply {
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    subtitleView?.visibility = View.GONE
                    this.player = nativePlayer
                }
            },
            update = { view ->
                if (view.player != nativePlayer) {
                    Log.d("PlayerScreen", "Updating PlayerView player: ${nativePlayer != null}")
                    view.player = nativePlayer
                }
                view.useController = false
                view.subtitleView?.visibility = View.GONE
                view.resizeMode = resizeMode
            },
            modifier = modifier.focusProperties { canFocus = false }
        )
    }
}

@Composable
actual fun PlayerSubtitles(cues: PlayerCues, style: PlayerSubtitleStyle, modifier: Modifier) {
    val textColour = when (style.colour) { "yellow" -> android.graphics.Color.YELLOW; "cyan" -> android.graphics.Color.CYAN; else -> android.graphics.Color.WHITE }
    val backgroundColour = android.graphics.Color.argb((style.backgroundOpacity.coerceIn(0f, 0.9f) * 255).toInt(), 12, 12, 14)
    AndroidView(factory = { context -> SubtitleView(context).apply { setApplyEmbeddedStyles(false) } }, update = { view ->
        view.setCues(cues.cues)
        view.setFractionalTextSize(0.0533f * style.textScale)
        view.setStyle(CaptionStyleCompat(textColour, android.graphics.Color.TRANSPARENT, backgroundColour, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, android.graphics.Color.BLACK, android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)))
    }, modifier = modifier)
}
