package me.xdan.aperture.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Identifies something playable wherever it lives. Local files keep their database id; Jellyfin
 * items are an item id on the server a profile's account points at.
 */
@Serializable
sealed interface MediaKey {
    @Serializable @SerialName("local")
    data class Local(val id: Long) : MediaKey

    @Serializable @SerialName("jellyfin")
    data class Jellyfin(val accountId: Long, val itemId: String) : MediaKey

    /** A stable string form, for saved state, focus keys and the companion protocol. */
    val stableId: String
        get() = when (this) {
            is Local -> "local:$id"
            is Jellyfin -> "jellyfin:$accountId:$itemId"
        }

    companion object {
        fun parse(stableId: String): MediaKey? {
            val parts = stableId.split(':')
            return when (parts.firstOrNull()) {
                "local" -> parts.getOrNull(1)?.toLongOrNull()?.let(::Local)
                "jellyfin" -> {
                    val account = parts.getOrNull(1)?.toLongOrNull() ?: return null
                    val item = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return null
                    Jellyfin(account, item)
                }
                else -> null
            }
        }
    }
}
