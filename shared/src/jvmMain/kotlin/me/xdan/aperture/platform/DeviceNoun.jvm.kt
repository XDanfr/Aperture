package me.xdan.aperture.platform

actual val thisDeviceNoun: String = System.getProperty("os.name").orEmpty().lowercase().let { os ->
    when {
        "mac" in os -> "Mac"
        "windows" in os -> "PC"
        else -> "computer"
    }
}
