package me.xdan.aperture.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

// The jvm resources include the Android res directory, so both apps ship the same font file.
private val outfitBytes: ByteArray by lazy {
    checkNotNull(ApertureBrandFontFamilyLoader::class.java.getResourceAsStream("/font/outfit_variable.ttf")) {
        "Outfit font missing from resources"
    }.use { it.readBytes() }
}

private object ApertureBrandFontFamilyLoader

@OptIn(ExperimentalTextApi::class)
private fun outfitFont(weight: FontWeight, axisWeight: Int) = Font(
    identity = "outfit_variable_$axisWeight",
    data = outfitBytes,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(axisWeight))
)

@OptIn(ExperimentalTextApi::class)
actual val ApertureBrandFontFamily: FontFamily = FontFamily(
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
