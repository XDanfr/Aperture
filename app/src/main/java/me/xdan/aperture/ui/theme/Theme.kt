package me.xdan.aperture.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
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
    ApertureThemeOption("dynamic", "Artwork Dynamic [PRE]", Color(0xFFB7C8FF), Color(0xFFAFC7E8), Color(0xFFC6B7E8)),
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
    inversePrimary = InversePrimary,
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
    val target = remember(themeId, dynamicAccent) {
        val option = ApertureThemeOptions.firstOrNull { it.id == themeId } ?: ApertureThemeOptions.first()
        if (themeId == "dynamic") {
            dynamicArtworkColorScheme(dynamicAccent ?: option.primary)
        } else {
            apertureDarkColorScheme(themeId, option.primary, inversePrimaryForAccent(option.primary), option.secondary, option.tertiary)
        }
    }
    AnimatedApertureTheme(target, themeId == "classic", content)
}

@Composable
private fun AnimatedApertureTheme(target: ColorScheme, isMaterialTv: Boolean, content: @Composable () -> Unit) {
    val motion = remember { ApertureMotion() }
    val spec = remember(motion) { motion.brandFastEffects<Color>() }
    val primary by animateColorAsState(target.primary, spec, label = "themePrimary")
    val inversePrimary by animateColorAsState(target.inversePrimary, spec, label = "themeInversePrimary")
    val onPrimary by animateColorAsState(target.onPrimary, spec, label = "themeOnPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "themePrimaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "themeOnPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, spec, label = "themeSecondary")
    val onSecondary by animateColorAsState(target.onSecondary, spec, label = "themeOnSecondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, spec, label = "themeSecondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, spec, label = "themeOnSecondaryContainer")
    val tertiary by animateColorAsState(target.tertiary, spec, label = "themeTertiary")
    val onTertiary by animateColorAsState(target.onTertiary, spec, label = "themeOnTertiary")
    val tertiaryContainer by animateColorAsState(target.tertiaryContainer, spec, label = "themeTertiaryContainer")
    val onTertiaryContainer by animateColorAsState(target.onTertiaryContainer, spec, label = "themeOnTertiaryContainer")
    val scheme = target.copy(
        primary = primary, inversePrimary = inversePrimary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer
    )
    // Keep the static CompositionLocal's value stable throughout the fade. Only consumers
    // reading changed colour state need recomposition; spacing, motion and shape readers skip it.
    val tokens = remember {
        ApertureTokens(
            colorScheme = scheme.toApertureColorScheme(),
            typography = ApertureTypography(Typography),
            shapes = ApertureShapes(),
            motion = motion,
            spacing = ApertureSpacing(),
            elevation = ApertureElevation(),
            brandAccent = if (isMaterialTv) Primary else primary,
            isMaterialTv = isMaterialTv
        )
    }
    SideEffect {
        tokens.colorScheme.updateFromMaterial(scheme)
        tokens.brandAccent = if (isMaterialTv) Primary else primary
        tokens.isMaterialTv = isMaterialTv
    }
    CompositionLocalProvider(LocalApertureTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
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

    val isMaterialTv: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalApertureTokens.current.isMaterialTv
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
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
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

/** Builds the TV Material roles without a composition, including each accent's inverse role. */
internal fun apertureDarkColorScheme(
    themeId: String,
    primary: Color,
    inversePrimary: Color,
    secondary: Color,
    tertiary: Color
): ColorScheme {
    return if (themeId == "purple") {
        DarkColorScheme
    } else {
        darkColorScheme(
            primary = primary,
            inversePrimary = inversePrimary,
            onPrimary = if (themeId == "classic") Color(0xFF1A191C) else Color(0xFF151218),
            primaryContainer = if (themeId == "classic") Color(0xFFE7E2E8) else primary.copy(alpha = 0.30f),
            onPrimaryContainer = if (themeId == "classic") Color(0xFF454047) else primary,
            secondary = secondary,
            onSecondary = Color(0xFF151218),
            secondaryContainer = if (themeId == "classic") Color(0xFFDCD7DE) else secondary.copy(alpha = 0.20f),
            onSecondaryContainer = if (themeId == "classic") Color(0xFF464047) else secondary,
            tertiary = tertiary,
            onTertiary = Color(0xFF20151A),
            tertiaryContainer = if (themeId == "classic") Color(0xFFD8D2D9) else tertiary.copy(alpha = 0.20f),
            onTertiaryContainer = if (themeId == "classic") Color(0xFF443D45) else tertiary,
            background = Color(0xFF111014),
            onBackground = Color(0xFFF1ECF2),
            surface = Color(0xFF111014),
            onSurface = Color(0xFFF1ECF2),
            surfaceVariant = Color(0xFF252229),
            onSurfaceVariant = Color(0xFFE6E0E8)
        )
    }
}

private fun ApertureColorScheme.updateFromMaterial(scheme: ColorScheme) {
    surfaceBright = scheme.surfaceVariant
    surfaceDim = scheme.surface
    surfaceContainer = scheme.surfaceVariant
    surfaceContainerLow = scheme.surface
    surfaceContainerHigh = scheme.surfaceVariant
    surfaceContainerHighest = scheme.surfaceVariant
    mediaCardBackground = scheme.surfaceVariant
    focusedMediaCardBackground = scheme.primaryContainer
    heroBackground = scheme.surface
    playbackOverlay = scheme.background.copy(alpha = 0.8f)
    metadataBackground = scheme.surface.copy(alpha = 0.6f)
    shelfBackground = Color.Transparent
    primary = scheme.primary
    onPrimary = scheme.onPrimary
    primaryContainer = scheme.primaryContainer
    onPrimaryContainer = scheme.onPrimaryContainer
    secondary = scheme.secondary
    background = scheme.background
    onBackground = scheme.onBackground
    surface = scheme.surface
    onSurface = scheme.onSurface
    surfaceVariant = scheme.surfaceVariant
    onSurfaceVariant = scheme.onSurfaceVariant
    border = scheme.border
    borderVariant = scheme.borderVariant
}
