package me.xdan.aperture.ui.theme

import androidx.compose.ui.graphics.Color
import me.xdan.aperture.companion.protocol.PhoneColors

/** shared by the TV app and the phone companion so both offer the same named palettes. */
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

/** the default palette; the phone companion uses it whenever the TV is on Artwork Dynamic. */
const val DefaultThemeId = "purple"
const val DynamicThemeId = "dynamic"

/** the TV theme that follows a paired phone's Material You colours. not in [ApertureThemeOptions]. */
const val PhoneThemeId = "phone"

/** a paired phone's Material You colours as a theme option, for swatches and scheme building. */
fun PhoneColors.toThemeOption(label: String = "Match phone") =
    ApertureThemeOption(PhoneThemeId, label, Color(primary), Color(secondary), Color(tertiary))

/** display name for a theme id, including the phone theme. */
fun apertureThemeLabel(themeId: String): String =
    if (themeId == PhoneThemeId) "Match phone"
    else ApertureThemeOptions.firstOrNull { it.id == themeId }?.label ?: "Aperture Purple"
