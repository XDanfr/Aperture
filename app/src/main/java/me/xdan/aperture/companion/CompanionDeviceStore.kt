package me.xdan.aperture.companion

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import me.xdan.aperture.companion.protocol.CompanionProtocol
import me.xdan.aperture.companion.protocol.PhoneColors
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PairedPhone(
    val deviceId: String,
    val name: String,
    /** SHA-256 of the bearer token; the token itself is never stored on the TV. */
    val tokenHash: String,
    val pairedAt: Long,
    val lastSeenAt: Long = pairedAt,
    /** the phone's Material You accents, when it supports them. */
    val colors: PhoneColors? = null
)

/** paired phones and this TV's stable identity. excluded from backup (see backup rules). */
@Singleton
class CompanionDeviceStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private val listSerializer = ListSerializer(PairedPhone.serializer())

    val tvId: String = prefs.getString(KEY_TV_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_TV_ID, it).apply()
    }

    val tvName: String
        get() = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<PairedPhone>> = _devices.asStateFlow()

    /** returns the new plain token, which is handed to the phone exactly once. */
    @Synchronized
    fun pair(deviceId: String, name: String): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val now = System.currentTimeMillis()
        val updated = _devices.value.filterNot { it.deviceId == deviceId } +
            PairedPhone(deviceId, name.take(64), hash(token), now)
        save(updated)
        return token
    }

    @Synchronized
    fun authenticate(token: String?): PairedPhone? {
        if (token.isNullOrBlank()) return null
        val hashed = hash(token)
        val device = _devices.value.firstOrNull {
            MessageDigest.isEqual(it.tokenHash.toByteArray(), hashed.toByteArray())
        } ?: return null
        val now = System.currentTimeMillis()
        // avoid rewriting preferences on every request; minute resolution is plenty.
        if (now - device.lastSeenAt > 60_000) {
            save(_devices.value.map { if (it.deviceId == device.deviceId) it.copy(lastSeenAt = now) else it })
        }
        return device
    }

    /** the phone whose colours the "Match phone" theme uses. */
    private val _themePhoneId = MutableStateFlow(prefs.getString(KEY_THEME_PHONE, null))
    val themePhoneId: StateFlow<String?> = _themePhoneId.asStateFlow()

    fun setThemePhone(deviceId: String) {
        prefs.edit().putString(KEY_THEME_PHONE, deviceId).apply()
        _themePhoneId.value = deviceId
    }

    @Synchronized
    fun updateColors(deviceId: String, colors: PhoneColors?) {
        if (_devices.value.none { it.deviceId == deviceId && it.colors != colors }) return
        save(_devices.value.map { if (it.deviceId == deviceId) it.copy(colors = colors) else it })
    }

    @Synchronized
    fun remove(deviceId: String) {
        save(_devices.value.filterNot { it.deviceId == deviceId })
    }

    private fun load(): List<PairedPhone> = runCatching {
        prefs.getString(KEY_DEVICES, null)?.let {
            CompanionProtocol.json.decodeFromString(listSerializer, it)
        }
    }.getOrNull().orEmpty()

    private fun save(devices: List<PairedPhone>) {
        prefs.edit().putString(KEY_DEVICES, CompanionProtocol.json.encodeToString(listSerializer, devices)).apply()
        _devices.value = devices
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val PREFS = "companion"
        const val KEY_TV_ID = "tv_id"
        const val KEY_DEVICES = "devices"
        const val KEY_THEME_PHONE = "theme_phone"
    }
}
