package me.xdan.aperture.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import me.xdan.aperture.ui.theme.tokens.*

data class ApertureThemeOption(
    val id: String,
    val label: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
) {
    val preview: Color get() = primary
}

val ApertureThemeOptions = listOf(
    ApertureThemeOption("purple", "Aperture Purple", Color(0xFFD0BCFF), Color(0xFFCCC2DC), Color(0xFFEFB8C8)),
    ApertureThemeOption("dynamic", "Artwork Dynamic [PRE-ALPHA]", Color(0xFFB7C8FF), Color(0xFFAFC7E8), Color(0xFFC6B7E8)),
    ApertureThemeOption("classic", "Material TV", Color.White, Color(0xFFE6E1E5), Color(0xFFD0C8D1)),
    ApertureThemeOption("green", "Emerald", Color(0xFF7DDA9A), Color(0xFFA4DDB5), Color(0xFFB9D98B)),
    ApertureThemeOption("red", "Cinema Red", Color(0xFFFFB4AB), Color(0xFFFFC9C3), Color(0xFFFFC17D)),
    ApertureThemeOption("orange", "Sunset Orange", Color(0xFFFFB86B), Color(0xFFFFD0A0), Color(0xFFFFC875)),
    ApertureThemeOption("blue", "Electric Blue", Color(0xFFA9C7FF), Color(0xFFB7D9FF), Color(0xFFBFC1FF)),
    ApertureThemeOption("teal", "Ocean Teal", Color(0xFF7CD8D2), Color(0xFFA4DCD7), Color(0xFFB8D6A5)),
    ApertureThemeOption("pink", "Neon Pink", Color(0xFFFFB0D0), Color(0xFFFFC5D9), Color(0xFFD6BCFF))
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
    val accent = if (themeId == "dynamic") dynamicAccent ?: option.primary else option.primary
    val secondary = if (themeId == "dynamic") accent.copy(alpha = 0.86f) else option.secondary
    val tertiary = if (themeId == "dynamic") accent.copy(alpha = 0.72f) else option.tertiary
    val animatedAccent = animateColorAsState(
        targetValue = accent,
        animationSpec = tween(durationMillis = 520),
        label = "apertureThemeAccent"
    ).value
    val animatedSecondary = animateColorAsState(
        targetValue = secondary,
        animationSpec = tween(durationMillis = 520),
        label = "apertureThemeSecondary"
    ).value
    val animatedTertiary = animateColorAsState(
        targetValue = tertiary,
        animationSpec = tween(durationMillis = 520),
        label = "apertureThemeTertiary"
    ).value

    val scheme = if (themeId == "purple") {
        DarkColorScheme
    } else {
        darkColorScheme(
            primary = animatedAccent,
            onPrimary = if (themeId == "classic") Color(0xFF1A191C) else Color(0xFF151218),
            primaryContainer = if (themeId == "classic") Color(0xFFE7E2E8) else animatedAccent.copy(alpha = 0.30f),
            onPrimaryContainer = if (themeId == "classic") Color(0xFF454047) else animatedAccent,
            secondary = animatedSecondary,
            onSecondary = Color(0xFF151218),
            secondaryContainer = if (themeId == "classic") Color(0xFFDCD7DE) else animatedSecondary.copy(alpha = 0.20f),
            onSecondaryContainer = if (themeId == "classic") Color(0xFF464047) else animatedSecondary,
            tertiary = animatedTertiary,
            onTertiary = Color(0xFF20151A),
            tertiaryContainer = if (themeId == "classic") Color(0xFFD8D2D9) else animatedTertiary.copy(alpha = 0.20f),
            onTertiaryContainer = if (themeId == "classic") Color(0xFF443D45) else animatedTertiary,
            background = Color(0xFF111014),
            onBackground = Color(0xFFF1ECF2),
            surface = Color(0xFF111014),
            onSurface = Color(0xFFF1ECF2),
            surfaceVariant = Color(0xFF252229),
            onSurfaceVariant = Color(0xFFE6E0E8)
        )
    }

    val apertureColorScheme = remember {
        scheme.toApertureColorScheme()
    }

    // Efficiently update semantic roles when the base Material scheme changes
    apertureColorScheme.updateFrom(scheme.toApertureColorScheme())

    val tokens = remember(apertureColorScheme, themeId) {
        ApertureTokens(
            colorScheme = apertureColorScheme,
            typography = ApertureTypography(Typography),
            shapes = ApertureShapes(),
            motion = ApertureMotion(),
            spacing = ApertureSpacing(),
            elevation = ApertureElevation(),
            brandAccent = if (themeId == "classic") Primary else animatedAccent
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
    val colorScheme: ApertureColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalApertureTokens.current.colorScheme

    val shapes: ApertureShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalApertureTokens.current.shapes

    val motion: ApertureMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalApertureTokens.current.motion

    val spacing: ApertureSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalApertureTokens.current.spacing

    val elevation: ApertureElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalApertureTokens.current.elevation

    val typography: ApertureTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalApertureTokens.current.typography

    val brandAccent: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalApertureTokens.current.brandAccent
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
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        border = border,
        borderVariant = borderVariant
    )
}
