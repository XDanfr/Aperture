package me.xdan.aperture.data.subtitles

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.xdan.aperture.BuildConfig
import me.xdan.aperture.data.remote.api.OpenSubtitlesApi
import me.xdan.aperture.data.remote.api.OpenSubtitlesLoginRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenSubtitlesSessionManager @Inject constructor(
    private val api: OpenSubtitlesApi,
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<OpenSubtitlesSessionState>(restoreSession())
    val state: StateFlow<OpenSubtitlesSessionState> = _state

    suspend fun login(username: String, password: String) {
        if (BuildConfig.OPENSUBTITLES_API_KEY.isBlank()) {
            _state.value = OpenSubtitlesSessionState.Error(
                "This Aperture build does not include an OpenSubtitles API key."
            )
            return
        }
        if (username.isBlank() || password.isBlank()) {
            _state.value = OpenSubtitlesSessionState.Error("Enter your username and password.")
            return
        }
        _state.value = OpenSubtitlesSessionState.SigningIn
        runCatching {
            api.login(
                OpenSubtitlesLoginRequest(username.trim(), password),
                BuildConfig.OPENSUBTITLES_API_KEY
            )
        }.onSuccess { response ->
            val expiry = System.currentTimeMillis() + TOKEN_LIFETIME_MS
            val baseUrl = normaliseBaseUrl(response.baseUrl)
            preferences.edit()
                .putString(KEY_USERNAME, username.trim())
                .putString(KEY_TOKEN, response.token)
                .putString(KEY_BASE_URL, baseUrl)
                .putLong(KEY_EXPIRY, expiry)
                .apply()
            _state.value = OpenSubtitlesSessionState.SignedIn(username.trim())
        }.onFailure { error ->
            clearStoredSession()
            _state.value = OpenSubtitlesSessionState.Error(
                error.message ?: "OpenSubtitles sign-in failed."
            )
        }
    }

    fun logout() {
        clearStoredSession()
        _state.value = OpenSubtitlesSessionState.SignedOut
    }

    fun tokenOrNull(): String? {
        if (System.currentTimeMillis() >= preferences.getLong(KEY_EXPIRY, 0L)) {
            logout()
            return null
        }
        return preferences.getString(KEY_TOKEN, null)
    }

    fun downloadEndpoint(): String =
        apiEndpoint("download")

    fun searchEndpoint(): String = apiEndpoint("subtitles")

    private fun restoreSession(): OpenSubtitlesSessionState {
        val username = preferences.getString(KEY_USERNAME, null)
        val token = preferences.getString(KEY_TOKEN, null)
        val expiry = preferences.getLong(KEY_EXPIRY, 0L)
        return if (!username.isNullOrBlank() && !token.isNullOrBlank() && expiry > System.currentTimeMillis()) {
            OpenSubtitlesSessionState.SignedIn(username)
        } else {
            clearStoredSession()
            OpenSubtitlesSessionState.SignedOut
        }
    }

    private fun clearStoredSession() {
        preferences.edit().clear().apply()
    }

    private fun normaliseBaseUrl(baseUrl: String): String {
        val withScheme = if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            baseUrl
        } else {
            "https://$baseUrl"
        }
        return withScheme.trimEnd('/')
    }

    private fun apiEndpoint(endpoint: String): String {
        val base = preferences.getString(KEY_BASE_URL, null)?.trimEnd('/')
            ?: OpenSubtitlesApi.BASE_URL.trimEnd('/')
        val apiBase = if (base.endsWith("/api/v1")) base else "$base/api/v1"
        return "$apiBase/$endpoint"
    }

    private companion object {
        const val PREFERENCES_NAME = "opensubtitles_session"
        const val KEY_USERNAME = "username"
        const val KEY_TOKEN = "token"
        const val KEY_BASE_URL = "base_url"
        const val KEY_EXPIRY = "expiry"
        const val TOKEN_LIFETIME_MS = 24L * 60L * 60L * 1000L
    }
}

sealed interface OpenSubtitlesSessionState {
    data object SignedOut : OpenSubtitlesSessionState
    data object SigningIn : OpenSubtitlesSessionState
    data class SignedIn(val username: String) : OpenSubtitlesSessionState
    data class Error(val message: String) : OpenSubtitlesSessionState
}
