package me.xdan.aperture.ui.platform

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

private val blankCursor: PointerIcon by lazy {
    runCatching {
        PointerIcon(
            Toolkit.getDefaultToolkit().createCustomCursor(
                BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "aperture-hidden"
            )
        )
    }.getOrDefault(PointerIcon.Default)
}

actual fun hiddenPointerIcon(): PointerIcon = blankCursor
