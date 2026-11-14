package me.xdan.aperture.domain.model

enum class AmbientModeType(val preferenceValue: String) {
    CINEMATIC("cinematic"),
    POSTER_WALL("poster_wall");

    companion object {
        fun fromPreference(value: String?): AmbientModeType =
            entries.firstOrNull { it.preferenceValue == value } ?: CINEMATIC
    }
}

enum class AmbientBrandPlacement(val preferenceValue: String) {
    TOP_LEFT("top_left"),
    CENTRE("centre");

    companion object {
        fun fromPreference(value: String?): AmbientBrandPlacement =
            entries.firstOrNull { it.preferenceValue == value } ?: TOP_LEFT
    }
}

data class AmbientSettings(
    val mode: AmbientModeType = AmbientModeType.CINEMATIC,
    val wallBrandPlacement: AmbientBrandPlacement = AmbientBrandPlacement.TOP_LEFT,
    val showClock: Boolean = false
)
