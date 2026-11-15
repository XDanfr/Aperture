package me.xdan.aperture.mobile.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.xdan.aperture.companion.protocol.DeviceKind
import java.util.UUID

data class PairedTv(
    val tvId: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
    /** A [me.xdan.aperture.companion.protocol.DeviceKind]. */
    val deviceKind: String = DeviceKind.TV
) {
    val baseUrl: String get() = "http://${if (':' in host) "[$host]" else host}:$port"
}

/** phone-side theme choice. TV sync and Material You are phone-only options. */
object ThemeChoice {
    const val SYNC_TV = "sync_tv"
    const val MATERIAL_YOU = "material_you"
}

/** small key-value store; the pairing file is excluded from backups. */
class CompanionSettings(context: Context) {
    private val pairing = context.getSharedPreferences("companion_pairing", Context.MODE_PRIVATE)
    private val prefs = context.getSharedPreferences("companion_settings", Context.MODE_PRIVATE)

    val deviceId: String = pairing.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
        pairing.edit().putString(KEY_DEVICE_ID, it).apply()
    }

    val deviceName: String = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    private val _pairedTv = MutableStateFlow(loadTv())
    val pairedTv: StateFlow<PairedTv?> = _pairedTv.asStateFlow()

    private val _themeChoice = MutableStateFlow(prefs.getString(KEY_THEME, ThemeChoice.SYNC_TV) ?: ThemeChoice.SYNC_TV)
    val themeChoice: StateFlow<String> = _themeChoice.asStateFlow()

    /** last theme the TV reported, so Sync with TV starts in the right colours offline. */
    private val _lastTvTheme = MutableStateFlow(prefs.getString(KEY_TV_THEME, null))
    val lastTvTheme: StateFlow<String?> = _lastTvTheme.asStateFlow()

    private val _onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun savePairedTv(tv: PairedTv) {
        pairing.edit()
            .putString(KEY_TV_ID, tv.tvId)
            .putString(KEY_TV_NAME, tv.name)
            .putString(KEY_TV_HOST, tv.host)
            .putInt(KEY_TV_PORT, tv.port)
            .putString(KEY_TV_TOKEN, tv.token)
            .putString(KEY_TV_KIND, tv.deviceKind)
            .apply()
        _pairedTv.value = tv
    }

    fun updateAddress(host: String, port: Int) {
        val tv = _pairedTv.value ?: return
        if (tv.host == host && tv.port == port) return
        savePairedTv(tv.copy(host = host, port = port))
    }

    fun updateDeviceKind(kind: String) {
        val tv = _pairedTv.value ?: return
        if (tv.deviceKind == kind) return
        savePairedTv(tv.copy(deviceKind = kind))
    }

    fun clearPairedTv() {
        pairing.edit().remove(KEY_TV_ID).remove(KEY_TV_NAME).remove(KEY_TV_HOST)
            .remove(KEY_TV_PORT).remove(KEY_TV_TOKEN).remove(KEY_TV_KIND).apply()
        _pairedTv.value = null
    }

    fun setThemeChoice(choice: String) {
        prefs.edit().putString(KEY_THEME, choice).apply()
        _themeChoice.value = choice
    }

    fun setLastTvTheme(themeId: String) {
        if (_lastTvTheme.value == themeId) return
        prefs.edit().putString(KEY_TV_THEME, themeId).apply()
        _lastTvTheme.value = themeId
    }

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, done).apply()
        _onboardingDone.value = done
    }

    private fun loadTv(): PairedTv? {
        val token = pairing.getString(KEY_TV_TOKEN, null) ?: return null
        return PairedTv(
            tvId = pairing.getString(KEY_TV_ID, null).orEmpty(),
            name = pairing.getString(KEY_TV_NAME, null) ?: "Aperture TV",
            host = pairing.getString(KEY_TV_HOST, null) ?: return null,
            port = pairing.getInt(KEY_TV_PORT, 0).takeIf { it > 0 } ?: return null,
            token = token,
            deviceKind = pairing.getString(KEY_TV_KIND, null) ?: DeviceKind.TV
        )
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_TV_ID = "tv_id"
        const val KEY_TV_NAME = "tv_name"
        const val KEY_TV_HOST = "tv_host"
        const val KEY_TV_PORT = "tv_port"
        const val KEY_TV_TOKEN = "tv_token"
        const val KEY_TV_KIND = "tv_kind"
        const val KEY_THEME = "theme_choice"
        const val KEY_TV_THEME = "last_tv_theme"
        const val KEY_ONBOARDED = "onboarding_done"
    }
}
