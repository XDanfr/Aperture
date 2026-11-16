package me.xdan.aperture.data.jellyfin

import me.xdan.aperture.platform.KeyValueStores
import java.util.UUID

/**
 * Access tokens and this install's id, in a store excluded from device backups (see the Android
 * backup rules). A restored or copied database therefore has accounts but no tokens, and those
 * profiles simply sign in again.
 */
class JellyfinCredentials(stores: KeyValueStores) {
    private val prefs = stores.open(STORE)

    /** Random per install, so a backup restored onto another device doesn't share sessions. */
    val installId: String = prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_INSTALL_ID, it).apply()
    }

    fun token(accountId: Long): String? = prefs.getString(tokenKey(accountId), null)

    fun saveToken(accountId: Long, token: String) {
        prefs.edit().putString(tokenKey(accountId), token).apply()
    }

    fun forget(accountId: Long) {
        prefs.edit().remove(tokenKey(accountId)).apply()
    }

    /**
     * Jellyfin keeps one session per device id and revokes the old token when another user signs in
     * with the same id, so each profile presents itself as its own device.
     */
    fun deviceId(profileId: Long): String = "aperture-$installId-p$profileId"

    private fun tokenKey(accountId: Long) = "token_$accountId"

    companion object {
        /** Also named in the Android backup and data extraction rules. */
        const val STORE = "jellyfin_credentials"
        private const val KEY_INSTALL_ID = "install_id"
    }
}
