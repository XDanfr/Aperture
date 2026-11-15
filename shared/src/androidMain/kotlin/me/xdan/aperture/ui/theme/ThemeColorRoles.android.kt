package me.xdan.aperture.ui.theme

import com.google.android.material.color.MaterialColors

internal actual fun materialColorRoles(color: Int, isLightTheme: Boolean): MaterialColorRoles =
    MaterialColors.getColorRoles(color, isLightTheme).let {
        MaterialColorRoles(it.accent, it.onAccent, it.accentContainer, it.onAccentContainer)
    }
