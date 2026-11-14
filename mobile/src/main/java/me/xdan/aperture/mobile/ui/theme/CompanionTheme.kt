package me.xdan.aperture.mobile.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.google.android.material.color.MaterialColors
import me.xdan.aperture.mobile.data.ThemeChoice
import me.xdan.aperture.mobile.data.PHONE_PALETTE_PREFIX
import me.xdan.aperture.mobile.data.parsePhonePalette
import me.xdan.aperture.companion.protocol.PhoneColors
import me.xdan.aperture.ui.theme.PhoneThemeId
import me.xdan.aperture.ui.theme.toThemeOption
import me.xdan.aperture.ui.component.ApertureBrandPalette
import me.xdan.aperture.ui.theme.ApertureBrandFontFamily
import me.xdan.aperture.ui.theme.ApertureThemeOption
import me.xdan.aperture.ui.theme.ApertureThemeOptions
import me.xdan.aperture.ui.theme.DefaultThemeId
import me.xdan.aperture.ui.theme.DynamicThemeId
import me.xdan.aperture.ui.theme.tokens.ApertureMotion

/** palettes offered on the phone: the TV's named palettes, minus Artwork Dynamic. */
val PhoneThemeOptions: List<ApertureThemeOption> = ApertureThemeOptions.filterNot { it.id == DynamicThemeId }

fun supportsMaterialYou(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** resolves the palette id to use. sync follows the TV; Artwork Dynamic falls back to purple. */
fun resolvePaletteId(choice: String, tvThemeId: String?): String = when (choice) {
    ThemeChoice.SYNC_TV -> tvThemeId?.takeUnless { it == DynamicThemeId || it == PhoneThemeId } ?: DefaultThemeId
    ThemeChoice.MATERIAL_YOU -> ThemeChoice.MATERIAL_YOU
    else -> choice.takeIf { id -> PhoneThemeOptions.any { it.id == id } } ?: DefaultThemeId
}

// Aperture's cinematic neutrals, shared with the TV's dark scheme.
private val Ink = Color(0xFF111014)
private val OnInk = Color(0xFFF1ECF2)

private fun roles(color: Color) = MaterialColors.getColorRoles(color.toArgb(), false)

/** builds a full M3 dark scheme for one of Aperture's named palettes. */
fun aperturePhoneScheme(option: ApertureThemeOption): ColorScheme {
    if (option.id == DefaultThemeId) {
        // the exact TV purple tokens.
        return apertureNeutrals(
            darkColorScheme(
                primary = me.xdan.aperture.ui.theme.Primary,
                onPrimary = me.xdan.aperture.ui.theme.OnPrimary,
                primaryContainer = me.xdan.aperture.ui.theme.PrimaryContainer,
                onPrimaryContainer = me.xdan.aperture.ui.theme.OnPrimaryContainer,
                inversePrimary = me.xdan.aperture.ui.theme.InversePrimary,
                secondary = me.xdan.aperture.ui.theme.Secondary,
                onSecondary = me.xdan.aperture.ui.theme.OnSecondary,
                secondaryContainer = me.xdan.aperture.ui.theme.SecondaryContainer,
                onSecondaryContainer = me.xdan.aperture.ui.theme.OnSecondaryContainer,
                tertiary = me.xdan.aperture.ui.theme.Tertiary,
                onTertiary = me.xdan.aperture.ui.theme.OnTertiary,
                tertiaryContainer = me.xdan.aperture.ui.theme.TertiaryContainer,
                onTertiaryContainer = me.xdan.aperture.ui.theme.OnTertiaryContainer,
                error = me.xdan.aperture.ui.theme.Error,
                onError = me.xdan.aperture.ui.theme.OnError,
                errorContainer = me.xdan.aperture.ui.theme.ErrorContainer,
                onErrorContainer = me.xdan.aperture.ui.theme.OnErrorContainer
            ),
            tint = me.xdan.aperture.ui.theme.Primary
        )
    }
    if (option.id == "classic") {
        // Material TV: monochrome accents on the same neutral stage.
        return apertureNeutrals(
            darkColorScheme(
                primary = Color.White, onPrimary = Color(0xFF1A191C),
                primaryContainer = Color(0xFFE7E2E8), onPrimaryContainer = Color(0xFF454047),
                inversePrimary = Color(0xFF5F5C63),
                secondary = option.secondary, onSecondary = Color(0xFF1A191C),
                secondaryContainer = Color(0xFF3A373D), onSecondaryContainer = Color(0xFFE6E1E5),
                tertiary = option.tertiary, onTertiary = Color(0xFF1A191C),
                tertiaryContainer = Color(0xFF3D3940), onTertiaryContainer = Color(0xFFE8E0E9)
            ),
            tint = Color.White
        )
    }
    val p = roles(option.primary)
    val s = roles(option.secondary)
    val t = roles(option.tertiary)
    return apertureNeutrals(
        darkColorScheme(
            primary = Color(p.accent), onPrimary = Color(p.onAccent),
            primaryContainer = Color(p.accentContainer), onPrimaryContainer = Color(p.onAccentContainer),
            inversePrimary = Color(MaterialColors.getColorRoles(option.primary.toArgb(), true).accent),
            secondary = Color(s.accent), onSecondary = Color(s.onAccent),
            secondaryContainer = Color(s.accentContainer), onSecondaryContainer = Color(s.onAccentContainer),
            tertiary = Color(t.accent), onTertiary = Color(t.onAccent),
            tertiaryContainer = Color(t.accentContainer), onTertiaryContainer = Color(t.onAccentContainer)
        ),
        tint = option.primary
    )
}

/** tone-based surface containers over Aperture's near-black, lightly tinted by the accent. */
private fun apertureNeutrals(scheme: ColorScheme, tint: Color): ColorScheme {
    fun level(alpha: Float, base: Color = Color(0xFF1D1B21)): Color = lerpColor(base, tint, alpha)
    return scheme.copy(
        background = Ink,
        onBackground = OnInk,
        surface = Ink,
        onSurface = OnInk,
        surfaceDim = Ink,
        surfaceBright = level(0.06f, Color(0xFF3A383F)),
        surfaceContainerLowest = Color(0xFF0C0B0F),
        surfaceContainerLow = level(0.03f, Color(0xFF19171C)),
        surfaceContainer = level(0.04f, Color(0xFF1D1B21)),
        surfaceContainerHigh = level(0.05f, Color(0xFF28252C)),
        surfaceContainerHighest = level(0.06f, Color(0xFF333037)),
        surfaceVariant = Color(0xFF252229),
        onSurfaceVariant = Color(0xFFCBC4CF),
        outline = Color(0xFF948F99),
        outlineVariant = Color(0xFF49454E),
        inverseSurface = OnInk,
        inverseOnSurface = Color(0xFF322F35),
        surfaceTint = scheme.primary
    )
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f
)

/** phone type scale: M3 defaults set in Aperture's Outfit, like the TV. */
private val CompanionTypography: Typography = Typography().let { base ->
    fun TextStyle.outfit() = copy(fontFamily = ApertureBrandFontFamily)
    base.copy(
        displayLarge = base.displayLarge.outfit(), displayMedium = base.displayMedium.outfit(),
        displaySmall = base.displaySmall.outfit(), headlineLarge = base.headlineLarge.outfit(),
        headlineMedium = base.headlineMedium.outfit(), headlineSmall = base.headlineSmall.outfit(),
        titleLarge = base.titleLarge.outfit(), titleMedium = base.titleMedium.outfit(),
        titleSmall = base.titleSmall.outfit(), bodyLarge = base.bodyLarge.outfit(),
        bodyMedium = base.bodyMedium.outfit(), bodySmall = base.bodySmall.outfit(),
        labelLarge = base.labelLarge.outfit(), labelMedium = base.labelMedium.outfit(),
        labelSmall = base.labelSmall.outfit()
    )
}

/** shared Aperture motion tokens; the phone uses the same expressive springs as the TV. */
val CompanionMotion = ApertureMotion()

fun ColorScheme.brandPalette() = ApertureBrandPalette(
    blade = inversePrimary,
    overlap = onPrimary,
    play = primary,
    background = background
)

/** the colour scheme a palette id resolves to, outside composition. */
fun companionColorScheme(context: android.content.Context, paletteId: String, dark: Boolean): ColorScheme =
    if (paletteId == ThemeChoice.MATERIAL_YOU && supportsMaterialYou()) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val option = parsePhonePalette(paletteId)?.toThemeOption()
            ?: PhoneThemeOptions.firstOrNull { it.id == paletteId } ?: PhoneThemeOptions.first()
        aperturePhoneScheme(option)
    }

/** this phone's Material You accents (dark roles), as reported to the TV. null below Android 12. */
fun currentPhoneColors(context: android.content.Context): PhoneColors? {
    if (!supportsMaterialYou()) return null
    val scheme = dynamicDarkColorScheme(context)
    return PhoneColors(
        primary = scheme.primary.toArgb(),
        secondary = scheme.secondary.toArgb(),
        tertiary = scheme.tertiary.toArgb(),
        inversePrimary = scheme.inversePrimary.toArgb()
    )
}

/** splash style for a palette (Android 13+). Material You needs Android 14's system role colours. */
fun splashThemeFor(paletteId: String): Int = when (paletteId) {
    ThemeChoice.MATERIAL_YOU -> if (Build.VERSION.SDK_INT >= 34) me.xdan.aperture.mobile.R.style.Theme_ApertureCompanion_Splash_MaterialYou
        else me.xdan.aperture.mobile.R.style.Theme_ApertureCompanion_Splash_Purple
    "classic" -> me.xdan.aperture.mobile.R.style.Theme_ApertureCompanion_Splash_Classic
    "green" -> me.xdan.aperture.mobile.R.style.Theme_ApertureCompanion_Splash_Green
    "red" -> me.xdan.aperture.mobile.R.style.Theme_ApertureCompanion_Splash_Red
    "orange" -> me.xdan.aperture.mobile.R.style.Theme_ApertureCompanion_Splash_Orange
    "blue" -> me.xdan.aperture.mobile.R.style.Theme_ApertureCompanion_Splash_Blue
    "teal" -> me.xdan.aperture.mobile.R.style.Theme_ApertureCompanion_Splash_Teal
    "pink" -> me.xdan.aperture.mobile.R.style.Theme_ApertureCompanion_Splash_Pink
    else -> me.xdan.aperture.mobile.R.style.Theme_ApertureCompanion_Splash_Purple
}

/** the palette id whose colours a splash style shows (inverse of [splashThemeFor]). */
fun splashPaletteIdFor(paletteId: String?): String = when {
    paletteId == null -> DefaultThemeId
    paletteId == ThemeChoice.MATERIAL_YOU && Build.VERSION.SDK_INT < 34 -> DefaultThemeId
    // another phone's colours have no splash style; the splash shows purple.
    paletteId.startsWith(PHONE_PALETTE_PREFIX) -> DefaultThemeId
    else -> paletteId
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompanionTheme(
    paletteId: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val target = remember(paletteId, dark) { companionColorScheme(context, paletteId, dark) }
    MaterialExpressiveTheme(
        colorScheme = target.animated(),
        motionScheme = MotionScheme.expressive(),
        typography = CompanionTypography,
        content = content
    )
}

/** colour changes use effects motion: they cross-fade, never overshoot. */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = remember { CompanionMotion.brandFastEffects<Color>() }
    @Composable fun Color.anim(label: String): Color = animateColorAsState(this, spec, label = label).value
    return copy(
        primary = primary.anim("primary"), onPrimary = onPrimary.anim("onPrimary"),
        primaryContainer = primaryContainer.anim("primaryContainer"), onPrimaryContainer = onPrimaryContainer.anim("onPrimaryContainer"),
        secondary = secondary.anim("secondary"), secondaryContainer = secondaryContainer.anim("secondaryContainer"),
        onSecondaryContainer = onSecondaryContainer.anim("onSecondaryContainer"),
        tertiary = tertiary.anim("tertiary"), tertiaryContainer = tertiaryContainer.anim("tertiaryContainer"),
        onTertiaryContainer = onTertiaryContainer.anim("onTertiaryContainer"),
        background = background.anim("background"), surface = surface.anim("surface"),
        onSurface = onSurface.anim("onSurface"), onSurfaceVariant = onSurfaceVariant.anim("onSurfaceVariant"),
        surfaceContainer = surfaceContainer.anim("surfaceContainer"),
        surfaceContainerLow = surfaceContainerLow.anim("surfaceContainerLow"),
        surfaceContainerHigh = surfaceContainerHigh.anim("surfaceContainerHigh"),
        surfaceContainerHighest = surfaceContainerHighest.anim("surfaceContainerHighest"),
        inversePrimary = inversePrimary.anim("inversePrimary")
    )
}
