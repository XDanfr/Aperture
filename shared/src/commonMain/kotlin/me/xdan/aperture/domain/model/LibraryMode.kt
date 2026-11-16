package me.xdan.aperture.domain.model

/** Where a profile's library comes from. */
enum class LibraryMode {
    /** Files on this device only. */
    LOCAL,
    /** A Jellyfin server only; Aperture is a client for it. */
    JELLYFIN,
    /** Both, with titles that exist in both places merged. */
    HYBRID;

    val usesLocal: Boolean get() = this != JELLYFIN
    val usesJellyfin: Boolean get() = this != LOCAL

    companion object {
        fun fromStored(value: String?): LibraryMode = entries.firstOrNull { it.name == value } ?: LOCAL
    }
}
