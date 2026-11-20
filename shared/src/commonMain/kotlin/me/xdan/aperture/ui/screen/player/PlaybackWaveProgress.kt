package me.xdan.aperture.ui.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import me.xdan.aperture.ui.theme.ApertureTheme

/**
 * Where the bar splits, as fractions of the runtime: the starts and ends of the title's Jellyfin
 * segments (intro, credits…). Empty for one unbroken bar.
 */
internal val LocalProgressSplits = compositionLocalOf { emptyList<Float>() }

/** Playback needs amplitude transitions that can reverse before their previous animation finishes. */
@Composable
internal fun PlaybackWaveProgress(
    progress: () -> Float,
    wavy: Boolean,
    modifier: Modifier = Modifier,
    splits: List<Float> = LocalProgressSplits.current
) = me.xdan.aperture.ui.component.SplitWaveProgress(
    progress = progress,
    wavy = wavy,
    indicatorColor = MaterialTheme.colorScheme.primary,
    trackColor = MaterialTheme.colorScheme.surfaceVariant,
    modifier = modifier,
    splits = splits,
    amplitudeSpec = ApertureTheme.motion.enter()
)
