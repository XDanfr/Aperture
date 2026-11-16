package me.xdan.aperture.data.jellyfin

import java.util.UUID

/**
 * Jellyfin writes ids as 32 hex digits without dashes (WebSocket messages, URLs); the SDK models
 * them as [UUID]s. Aperture stores the dashless form everywhere.
 */
internal object JellyfinIds {
    fun of(uuid: UUID): String = uuid.toString().replace("-", "")

    fun toUuid(id: String): UUID {
        val hex = id.replace("-", "")
        require(hex.length == 32) { "Not a Jellyfin id: $id" }
        return UUID.fromString(
            "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
        )
    }
}
