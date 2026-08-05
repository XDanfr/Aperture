package me.xdan.aperture.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import me.xdan.aperture.ui.theme.tokens.*

data class ApertureThemeOption(val id: String, val label: String, val preview: Color)

val ApertureThemeOptions = listOf(
    ApertureThemeOption("purple", "Aperture Purple", Color(0xFFD0BCFF)),
    ApertureThemeOption("dynamic", "Artwork Dynamic [PRE-ALPHA]", Color(0xFFB7C8FF)),
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
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
)

@Composable
fun ApertureTheme(
    themeId: String = "purple",
    dynamicAccent: Color? = null,
    content: @Composable () -> Unit
) {
    val option = ApertureThemeOptions.firstOrNull { it.id == themeId } ?: ApertureThemeOptions.first()
    val accent = if (themeId == "dynamic") dynamicAccent ?: option.preview else option.preview
    val animatedAccent = animateColorAsState(
        targetValue = accent,
        animationSpec = tween(durationMillis = 520),
        label = "apertureThemeAccent"
    ).value
    val scheme = if (themeId == "purple") DarkColorScheme else darkColorScheme(
        primary = animatedAccent,
        onPrimary = Color(0xFF151218),
        primaryContainer = animatedAccent.copy(alpha = 0.30f),
        onPrimaryContainer = animatedAccent,
        secondary = animatedAccent.copy(alpha = 0.82f),
        onSecondary = Color(0xFF151218),
        secondaryContainer = animatedAccent.copy(alpha = 0.20f),
        onSecondaryContainer = animatedAccent,
        background = Color(0xFF111014),
        onBackground = Color(0xFFF1ECF2),
        surface = Color(0xFF111014),
        onSurface = Color(0xFFF1ECF2),
        surfaceVariant = Color(0xFF252229),
        onSurfaceVariant = Color(0xFFE6E0E8)
    )

    val apertureColorScheme = remember(scheme) {
        scheme.toApertureColorScheme()
    }

    val tokens = remember(apertureColorScheme) {
        ApertureTokens(
            colorScheme = apertureColorScheme,
            typography = Typography,
            shapes = ApertureShapes(),
            motion = ApertureMotion(),
            spacing = ApertureSpacing(),
            elevation = ApertureElevation()
        )
    }

    CompositionLocalProvider(LocalApertureTokens provides tokens) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Accessor for Aperture expressive tokens.
 */
object ApertureTheme {
    val tokens: ApertureTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalApertureTokens.current
}

/**
 * Maps standard TV Material 3 color scheme to Aperture's expressive semantic roles.
 */
private fun ColorScheme.toApertureColorScheme(): ApertureColorScheme {
    return ApertureColorScheme(
        // Expressive base roles (approximated for Phase 1)
        surfaceBright = surfaceVariant,
        surfaceDim = surface,
        surfaceContainer = surfaceVariant,
        surfaceContainerLow = surface,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceVariant,

        // Semantic roles
        mediaCardBackground = surfaceVariant,
        focusedMediaCardBackground = primaryContainer,
        heroBackground = surface,
        playbackOverlay = background.copy(alpha = 0.8f),
        metadataBackground = surface.copy(alpha = 0.6f),
        shelfBackground = Color.Transparent,

        // Core brand
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface
    )
}
