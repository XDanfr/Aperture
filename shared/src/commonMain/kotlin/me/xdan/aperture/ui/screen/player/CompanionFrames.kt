package me.xdan.aperture.ui.screen.player

/** JPEG frames of what's playing, for the companion's chapter thumbnails; decoded apart from the player. */
expect class CompanionFrames() {
    suspend fun jpeg(source: String, positionMs: Long): ByteArray?
    fun clear()
}
