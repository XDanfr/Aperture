package me.xdan.aperture.ui.screen.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

/** "Skip Intro" and friends, shown while a Jellyfin segment plays. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SkipSegmentButton(label: String, focusRequester: FocusRequester, onSkip: () -> Unit) {
    Button(onClick = onSkip, modifier = Modifier.focusRequester(focusRequester)) {
        Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** Keys the focused Skip button handles itself instead of the player's shortcuts. */
internal fun Key.isSkipButtonKey(): Boolean =
    this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter
