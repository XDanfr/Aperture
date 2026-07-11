package me.xdan.aperture.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

data class ApertureThemeOption(val id: String, val label: String, val preview: Color)

val ApertureThemeOptions = listOf(
    ApertureThemeOption("purple", "Aperture Purple", Color(0xFFD0BCFF)),
    ApertureThemeOption("classic", "Material TV", Color(0xFFFFFFFF)),
    ApertureThemeOption("green", "Emerald", Color(0xFF7DDA9A)),
    ApertureThemeOption("red", "Cinema Red", Color(0xFFFFB4AB)),
    ApertureThemeOption("orange", "Sunset Orange", Color(0xFFFFB86B)),
    ApertureThemeOption("blue", "Electric Blue", Color(0xFFA9C7FF)),
    ApertureThemeOption("teal", "Ocean Teal", Color(0xFF7CD8D2)),
    ApertureThemeOption("pink", "Neon Pink", Color(0xFFFFB0D0))
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
)

@Composable
fun ApertureTheme(
    themeId: String = "purple",
    content: @Composable () -> Unit
) {
    val option = ApertureThemeOptions.firstOrNull { it.id == themeId } ?: ApertureThemeOptions.first()
    val scheme = if (themeId == "purple") DarkColorScheme else darkColorScheme(
        primary = option.preview,
        onPrimary = Color(0xFF151218),
        primaryContainer = option.preview.copy(alpha = 0.30f),
        onPrimaryContainer = option.preview,
        secondary = option.preview.copy(alpha = 0.82f),
        onSecondary = Color(0xFF151218),
        secondaryContainer = option.preview.copy(alpha = 0.20f),
        onSecondaryContainer = option.preview,
        background = Color(0xFF111014),
        onBackground = Color(0xFFF1ECF2),
        surface = Color(0xFF111014),
        onSurface = Color(0xFFF1ECF2),
        surfaceVariant = Color(0xFF252229),
        onSurfaceVariant = Color(0xFFE6E0E8)
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
