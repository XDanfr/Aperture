package me.xdan.aperture.mobile.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import me.xdan.aperture.mobile.data.TvRepository

/**
 * What to call the paired device in text: "TV", "Mac", "PC" or "computer". Before pairing it's
 * "TV or computer", since the phone doesn't know yet.
 */
val LocalDeviceNoun = staticCompositionLocalOf { TvRepository.UNKNOWN_NOUN }
