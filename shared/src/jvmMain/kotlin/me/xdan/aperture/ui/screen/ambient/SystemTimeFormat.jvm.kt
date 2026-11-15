package me.xdan.aperture.ui.screen.ambient

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberSystemTimeFormat(): () -> java.text.DateFormat =
    remember { { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) } }
