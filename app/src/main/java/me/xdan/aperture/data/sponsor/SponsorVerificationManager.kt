package me.xdan.aperture.data.sponsor

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import me.xdan.aperture.BuildConfig
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SponsorVerificationState {
    data object Idle : SponsorVerificationState
    data object RequestingCode : SponsorVerificationState
    data class AwaitingAuthorization(val userCode: String, val verificationUri: String) : SponsorVerificationState
    data object CheckingSponsorship : SponsorVerificationState
    data object Verified : SponsorVerificationState
    data object NotSponsor : SponsorVerificationState
    data class Error(val message: String) : SponsorVerificationState
}

@Singleton
class SponsorVerificationManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences("sponsor_verification", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<SponsorVerificationState>(SponsorVerificationState.Idle)
    val state: StateFlow<SponsorVerificationState> = _state
    private val _isVerified = MutableStateFlow(preferences.getBoolean(KEY_VERIFIED, false))
    val isVerified: StateFlow<Boolean> = _isVerified

    suspend fun verify() {
        if (_isVerified.value) return
        _state.value = SponsorVerificationState.RequestingCode
        runCatching { verifyInternal() }
            .onFailure { _state.value = SponsorVerificationState.Error(it.message ?: "Could not contact GitHub") }
    }

    fun resetState() {
        if (!_isVerified.value) _state.value = SponsorVerificationState.Idle
    }

    private suspend fun verifyInternal() = withContext(Dispatchers.IO) {
        val device = requestDeviceCode()
        val deviceCode = device.getString("device_code")
        val userCode = device.getString("user_code")
        val verificationUri = device.getString("verification_uri")
        var intervalSeconds = device.optLong("interval", 5L).coerceAtLeast(5L)
        val expiresAt = System.currentTimeMillis() + device.optLong("expires_in", 900L) * 1_000L
        _state.value = SponsorVerificationState.AwaitingAuthorization(userCode, verificationUri)

        var accessToken: String? = null
        while (System.currentTimeMillis() < expiresAt && accessToken == null) {
            delay(intervalSeconds * 1_000L)
            val tokenResult = requestAccessToken(deviceCode)
            accessToken = tokenResult.optString("access_token").takeIf(String::isNotBlank)
            when (tokenResult.optString("error")) {
                "authorization_pending", "" -> Unit
                "slow_down" -> intervalSeconds += 5L
                "expired_token" -> error("The GitHub code expired. Please try again.")
                "access_denied" -> error("GitHub authorisation was cancelled.")
                else -> error(tokenResult.optString("error_description", "GitHub authorisation failed."))
            }
        }
        val token = accessToken ?: error("The GitHub code expired. Please try again.")
        _state.value = SponsorVerificationState.CheckingSponsorship
        if (viewerSponsorsXDanfr(token)) {
            preferences.edit().putBoolean(KEY_VERIFIED, true).apply()
            _isVerified.value = true
            _state.value = SponsorVerificationState.Verified
        } else {
            _state.value = SponsorVerificationState.NotSponsor
        }
    }

    private fun requestDeviceCode(): JSONObject {
        val request = Request.Builder()
            .url("https://github.com/login/device/code")
            .header("Accept", "application/json")
            .header("User-Agent", "Aperture/${BuildConfig.VERSION_NAME}")
            .post(FormBody.Builder().add("client_id", CLIENT_ID).add("scope", "read:user").build())
            .build()
        return executeJson(request)
    }

    private fun requestAccessToken(deviceCode: String): JSONObject {
        val request = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .header("Accept", "application/json")
            .header("User-Agent", "Aperture/${BuildConfig.VERSION_NAME}")
            .post(FormBody.Builder().add("client_id", CLIENT_ID).add("device_code", deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code").build())
            .build()
        return executeJson(request)
    }

    private fun viewerSponsorsXDanfr(token: String): Boolean {
        val body = JSONObject().put(
            "query",
            "query { user(login: \"XDanfr\") { viewerIsSponsoring } }"
        ).toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.github.com/graphql")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Aperture/${BuildConfig.VERSION_NAME}")
            .post(body)
            .build()
        return executeJson(request).optJSONObject("data")?.optJSONObject("user")
            ?.optBoolean("viewerIsSponsoring", false) == true
    }

    private fun executeJson(request: Request): JSONObject = okHttpClient.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        check(response.isSuccessful) { "GitHub returned ${response.code}" }
        JSONObject(text)
    }

    private companion object {
        const val CLIENT_ID = "Ov23li6jOB4Fd3FcDvJ6"
        const val KEY_VERIFIED = "verified_sponsor"
    }
}
