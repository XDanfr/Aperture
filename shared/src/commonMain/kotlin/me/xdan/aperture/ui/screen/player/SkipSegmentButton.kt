package me.xdan.aperture.ui.screen.player

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
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
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

/**
 * "Skip Intro" and friends, shown while a Jellyfin segment plays: a large pill that fills with the
 * accent when focused, so it reads from the sofa. [previewFocused] draws the focused look (renders).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SkipSegmentButton(label: String, focusRequester: FocusRequester, onSkip: () -> Unit, previewFocused: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val idle = if (previewFocused) scheme.primary else scheme.surfaceVariant.copy(alpha = 0.92f)
    val idleContent = if (previewFocused) scheme.onPrimary else scheme.onSurface
    Button(
        onClick = onSkip,
        modifier = Modifier.focusRequester(focusRequester),
        shape = ButtonDefaults.shape(CircleShape),
        scale = ButtonDefaults.scale(scale = if (previewFocused) 1.08f else 1f, focusedScale = 1.08f, pressedScale = 0.95f),
        colors = ButtonDefaults.colors(
            containerColor = idle,
            contentColor = idleContent,
            focusedContainerColor = scheme.primary,
            focusedContentColor = scheme.onPrimary,
            pressedContainerColor = scheme.primary,
            pressedContentColor = scheme.onPrimary
        ),
        contentPadding = PaddingValues(start = 24.dp, end = 32.dp, top = 16.dp, bottom = 16.dp)
    ) {
        Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

/** Keys the focused Skip button handles itself instead of the player's shortcuts. */
internal fun Key.isSkipButtonKey(): Boolean =
    this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter

/**
 * The chapter under the scrubbing preview. In chapter mode it carries arrows, since Left and
 * Right then jump between chapters; outside it, a hint that Up switches it on.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ChapterPreviewLabel(chapters: List<PlayerChapter>, positionMs: Long, chapterMode: Boolean) {
    val chapter = chapters.at(positionMs) ?: return
    val scheme = MaterialTheme.colorScheme
    Text(
        text = if (chapterMode) "‹  ${chapter.name}  ›" else chapter.name,
        style = MaterialTheme.typography.titleMedium,
        color = if (chapterMode) scheme.primary else scheme.onSurfaceVariant
    )
}
