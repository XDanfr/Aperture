package me.xdan.aperture.ui.screen.ambient

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberSystemTimeFormat(): () -> java.text.DateFormat {
    val context = LocalContext.current
    return remember(context) { { DateFormat.getTimeFormat(context) } }
}
