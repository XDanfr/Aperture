package me.xdan.aperture.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import me.xdan.aperture.R

@OptIn(ExperimentalTextApi::class)
private fun outfitFont(weight: FontWeight, axisWeight: Int) = Font(
    resId = R.font.outfit_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axisWeight))
)

@OptIn(ExperimentalTextApi::class)
val ApertureBrandFontFamily = FontFamily(
    outfitFont(FontWeight.W100, 100),
    outfitFont(FontWeight.W200, 200),
    outfitFont(FontWeight.W300, 300),
    outfitFont(FontWeight.W400, 400),
    outfitFont(FontWeight.W500, 500),
    outfitFont(FontWeight.W600, 600),
    outfitFont(FontWeight.W700, 700),
    outfitFont(FontWeight.W800, 800),
    outfitFont(FontWeight.W900, 900)
)

/**
 * TV-oriented Material 3 type scale for Aperture.
 *
 * The hierarchy follows Material's semantic roles while increasing the sizes
 * enough for comfortable viewing distance. Outfit is used throughout the
 * hierarchy so the brand remains consistent while semantic weights preserve
 * the distinction between headings, supporting copy, and labels.
 */
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ApertureBrandFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
