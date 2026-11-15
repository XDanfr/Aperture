package me.xdan.aperture.ui.theme

import com.materialkolor.hct.Hct

// The same HCT re-toning MDC's MaterialColors.getColorRoles performs, with its tone constants.
private fun role(color: Int, tone: Int): Int = Hct.fromInt(color).withTone(tone.toDouble()).toInt()

internal actual fun materialColorRoles(color: Int, isLightTheme: Boolean): MaterialColorRoles =
    if (isLightTheme) {
        MaterialColorRoles(role(color, 40), role(color, 100), role(color, 90), role(color, 10))
    } else {
        MaterialColorRoles(role(color, 80), role(color, 20), role(color, 30), role(color, 90))
    }
