package me.xdan.aperture.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import me.xdan.aperture.core.R

@OptIn(ExperimentalTextApi::class)
private fun outfitFont(weight: FontWeight, axisWeight: Int) = Font(
    resId = R.font.outfit_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axisWeight))
)

@OptIn(ExperimentalTextApi::class)
actual val ApertureBrandFontFamily = FontFamily(
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
