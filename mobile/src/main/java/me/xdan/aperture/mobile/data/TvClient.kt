package me.xdan.aperture.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import me.xdan.aperture.companion.protocol.CompanionCommand
import me.xdan.aperture.companion.protocol.CompanionProtocol
import me.xdan.aperture.companion.protocol.PhoneColors
import me.xdan.aperture.companion.protocol.ErrorBody
import me.xdan.aperture.companion.protocol.LibrarySnapshot
import me.xdan.aperture.companion.protocol.PairBody
import me.xdan.aperture.companion.protocol.PairRequest
import me.xdan.aperture.companion.protocol.PairResponse
import me.xdan.aperture.companion.protocol.TvInfo
import me.xdan.aperture.companion.protocol.TvState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class TvApiException(val status: Int, val code: String?) : IOException("TV responded $status ${code.orEmpty()}")

/** thin HTTP client for the TV's companion endpoints. */
class TvClient(private val http: OkHttpClient) {
    private val json = CompanionProtocol.json
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val streamingHttp = http.newBuilder()
        // the TV sends a heartbeat every 15 s; anything longer means it went away.
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun info(baseUrl: String): TvInfo = get(baseUrl + CompanionProtocol.PATH_INFO, null, TvInfo.serializer())

    suspend fun requestPairing(baseUrl: String, deviceId: String, deviceName: String) {
        post(baseUrl + CompanionProtocol.PATH_PAIR_REQUEST, null, json.encodeToString(PairRequest.serializer(), PairRequest(deviceId, deviceName)))
    }

    suspend fun pair(baseUrl: String, code: String, deviceId: String, deviceName: String): PairResponse {
        val body = post(baseUrl + CompanionProtocol.PATH_PAIR, null, json.encodeToString(PairBody.serializer(), PairBody(code, deviceId, deviceName)))
        return json.decodeFromString(PairResponse.serializer(), body)
    }

    suspend fun unpair(tv: PairedTv) {
        post(tv.baseUrl + CompanionProtocol.PATH_UNPAIR, tv.token, "")
    }

    suspend fun state(tv: PairedTv): TvState = get(tv.baseUrl + CompanionProtocol.PATH_STATE, tv.token, TvState.serializer())

    suspend fun library(tv: PairedTv): LibrarySnapshot = get(tv.baseUrl + CompanionProtocol.PATH_LIBRARY, tv.token, LibrarySnapshot.serializer())

    suspend fun command(tv: PairedTv, command: CompanionCommand) {
        post(tv.baseUrl + CompanionProtocol.PATH_COMMAND, tv.token, json.encodeToString(CompanionCommand.serializer(), command))
    }

    suspend fun reportColors(tv: PairedTv, colors: PhoneColors) {
        post(tv.baseUrl + CompanionProtocol.PATH_COLORS, tv.token, json.encodeToString(PhoneColors.serializer(), colors))
    }

    /** blocks, delivering each TV state until the stream breaks. */
    suspend fun streamState(tv: PairedTv, onState: suspend (TvState) -> Unit) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(tv.baseUrl + CompanionProtocol.PATH_EVENTS)
            .header("Authorization", "Bearer ${tv.token}")
            .header("Accept", "text/event-stream")
            .build()
        streamingHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw TvApiException(response.code, null)
            val source = response.body!!.source()
            var event: String? = null
            val data = StringBuilder()
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: throw IOException("Stream closed")
                when {
                    line.isEmpty() -> {
                        if (event == "state" && data.isNotEmpty()) {
                            onState(json.decodeFromString(TvState.serializer(), data.toString()))
                        }
                        event = null
                        data.clear()
                    }
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                }
            }
        }
    }

    private suspend fun <T> get(url: String, token: String?, serializer: KSerializer<T>): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply { token?.let { header("Authorization", "Bearer $it") } }.build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw TvApiException(response.code, errorCode(body))
            json.decodeFromString(serializer, body)
        }
    }

    private suspend fun post(url: String, token: String?, body: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonType))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw TvApiException(response.code, errorCode(text))
            text
        }
    }

    private fun errorCode(body: String): String? =
        runCatching { json.decodeFromString(ErrorBody.serializer(), body).error }.getOrNull()
}
