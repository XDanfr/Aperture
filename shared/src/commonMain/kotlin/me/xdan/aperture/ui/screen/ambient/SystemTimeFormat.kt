package me.xdan.aperture.ui.screen.ambient

import androidx.compose.runtime.Composable

/** The system's short time format (12/24-hour as the user set it), re-read on each call. */
@Composable
internal expect fun rememberSystemTimeFormat(): () -> java.text.DateFormat
