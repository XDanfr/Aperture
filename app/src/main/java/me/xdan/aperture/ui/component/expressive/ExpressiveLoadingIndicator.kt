package me.xdan.aperture.ui.component.expressive

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.xdan.aperture.ui.theme.ApertureTheme

/** Temporary migration shim. Prefer LoadingIndicator directly. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Deprecated("Use androidx.compose.material3.LoadingIndicator directly.")
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    color: Color = ApertureTheme.colorScheme.primary
) {
    LoadingIndicator(
        modifier = modifier.size(size),
        color = color
    )
}
