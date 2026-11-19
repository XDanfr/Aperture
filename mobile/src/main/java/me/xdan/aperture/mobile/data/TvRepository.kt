package me.xdan.aperture.mobile.data

import me.xdan.aperture.companion.protocol.DeviceKind
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.xdan.aperture.companion.protocol.CompanionCommand
import me.xdan.aperture.companion.protocol.CompanionMedia
import me.xdan.aperture.companion.protocol.NowPlaying
import me.xdan.aperture.companion.protocol.PairingInvite
import me.xdan.aperture.companion.protocol.TvState
import me.xdan.aperture.companion.protocol.PhoneColors
import me.xdan.aperture.ui.theme.PhoneThemeId

sealed interface ConnectionState {
    data object NotPaired : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    /** the TV is unreachable: off, Aperture closed, or on another network. */
    data class Offline(val retryInSeconds: Int) : ConnectionState
    /** the TV removed this phone. */
    data object Revoked : ConnectionState
}

/** now playing plus the phone's receipt time, for smooth local progress. */
data class LiveNowPlaying(val value: NowPlaying, val receivedAtElapsed: Long) {
    fun positionAt(elapsedRealtime: Long): Long {
        if (!value.isPlaying || value.isBuffering) return value.positionMs
        val position = value.positionMs + (elapsedRealtime - receivedAtElapsed)
        return if (value.durationMs > 0) position.coerceAtMost(value.durationMs) else position
    }
}

sealed interface PairingResult {
    data object Success : PairingResult
    data class Failure(val message: String) : PairingResult
}

/**
 * keeps one live connection to the paired TV: an event stream for state, plus a
 * library snapshot refreshed whenever the TV's library revision changes.
 */
class TvRepository(
    private val scope: CoroutineScope,
    private val settings: CompanionSettings,
    private val client: TvClient,
    private val discovery: TvDiscovery,
    /** this phone's Material You colours, or null where unsupported (below Android 12). */
    private val phoneColors: () -> PhoneColors? = { null }
) {
    private val _connection = MutableStateFlow<ConnectionState>(
        if (settings.pairedTv.value == null) ConnectionState.NotPaired else ConnectionState.Connecting
    )
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _tvState = MutableStateFlow<TvState?>(null)
    val tvState: StateFlow<TvState?> = _tvState.asStateFlow()

    private val _nowPlaying = MutableStateFlow<LiveNowPlaying?>(null)
    val nowPlaying: StateFlow<LiveNowPlaying?> = _nowPlaying.asStateFlow()

    private val _library = MutableStateFlow<me.xdan.aperture.companion.protocol.LibrarySnapshot?>(null)
    val library: StateFlow<me.xdan.aperture.companion.protocol.LibrarySnapshot?> = _library.asStateFlow()

    private var connectJob: Job? = null
    private var libraryRevision: Long? = null
    private var foregroundCount = 0

    // region connection

    /** connect while the UI is visible; the TV only listens while Aperture is open anyway. */
    fun onForeground() {
        foregroundCount++
        if (connectJob?.isActive != true) {
            connectJob = scope.launch { connectLoop() }
        } else if (_connection.value == ConnectionState.Connected) {
            // a wallpaper change recreates the activity; keep the TV's copy of our colours fresh.
            val tv = settings.pairedTv.value
            val colors = phoneColors()
            if (tv != null && colors != null) scope.launch { runCatching { client.reportColors(tv, colors) } }
        }
    }

    fun onBackground() {
        foregroundCount = (foregroundCount - 1).coerceAtLeast(0)
        if (foregroundCount == 0) {
            connectJob?.cancel()
            connectJob = null
        }
    }

    fun reconnectNow() {
        connectJob?.cancel()
        connectJob = scope.launch { connectLoop() }
    }

    private suspend fun connectLoop() {
        var attempt = 0
        while (true) {
            val tv = settings.pairedTv.value
            if (tv == null) {
                _connection.value = ConnectionState.NotPaired
                return
            }
            if (_connection.value !is ConnectionState.Connected) _connection.value = ConnectionState.Connecting
            try {
                refreshLibrary(tv, force = _library.value == null)
                var reported = false
                client.streamState(tv) { state ->
                    attempt = 0
                    _connection.value = ConnectionState.Connected
                    if (!reported) {
                        reported = true
                        // lets the TV offer (and keep current) a theme matching this phone.
                        phoneColors()?.let { colors -> scope.launch { runCatching { client.reportColors(tv, colors) } } }
                    }
                    applyState(state)
                    if (state.libraryRevision != libraryRevision) {
                        scope.launch { runCatching { refreshLibrary(tv, force = true) } }
                    }
                }
            } catch (e: TvApiException) {
                if (e.status == 401) {
                    _connection.value = ConnectionState.Revoked
                    _nowPlaying.value = null
                    return
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // fall through to rediscovery and backoff.
            }
            _nowPlaying.value = null
            // DHCP may have moved the TV; find it again by its stable id.
            if (attempt % 2 == 1) rediscover(tv)
            val wait = BACKOFF_SECONDS[attempt.coerceAtMost(BACKOFF_SECONDS.lastIndex)]
            attempt++
            for (remaining in wait downTo 1) {
                _connection.value = ConnectionState.Offline(remaining)
                delay(1_000)
            }
        }
    }

    private suspend fun rediscover(tv: PairedTv) {
        if (tv.tvId.isBlank()) return
        val match = withTimeoutOrNull(4_000) {
            discovery.discover().firstOrNull { list -> list.any { it.tvId == tv.tvId } }
                ?.firstOrNull { it.tvId == tv.tvId }
        } ?: return
        settings.updateAddress(match.host, match.port)
    }

    private fun applyState(state: TvState) {
        _tvState.value = state
        settings.updateDeviceKind(state.deviceKind, state.deviceOs)
        settings.setLastTvTheme(syncedThemeId(state, settings.deviceId))
        _nowPlaying.value = state.nowPlaying?.let { LiveNowPlaying(it, SystemClock.elapsedRealtime()) }
    }

    private suspend fun refreshLibrary(tv: PairedTv, force: Boolean) {
        if (!force && _library.value != null) return
        val snapshot = client.library(tv)
        libraryRevision = snapshot.revision
        _library.value = snapshot
    }

    // endregion

    // region pairing

    suspend fun pairWithInvite(invite: PairingInvite): PairingResult =
        pair(invite.host, invite.port, invite.code, invite.tvId, invite.tvName, DeviceKind.noun(invite.deviceKind, invite.deviceOs))

    suspend fun requestPairing(tv: DiscoveredTv): PairingResult = try {
        client.requestPairing(tv.baseUrl, settings.deviceId, settings.deviceName)
        PairingResult.Success
    } catch (e: Exception) {
        PairingResult.Failure(e.toUserMessage(tv.noun))
    }

    /** [noun] names the device in error messages; a typed-in address could be either. */
    suspend fun pair(host: String, port: Int, code: String, tvId: String = "", tvName: String = "", noun: String = UNKNOWN_NOUN): PairingResult {
        val baseUrl = "http://${if (':' in host) "[$host]" else host}:$port"
        return try {
            val response = client.pair(baseUrl, code.filter(Char::isDigit), settings.deviceId, settings.deviceName)
            settings.savePairedTv(
                PairedTv(
                    tvId = response.tvId.ifBlank { tvId },
                    name = response.tvName.ifBlank { tvName },
                    host = host,
                    port = port,
                    token = response.token,
                    deviceKind = response.deviceKind,
                    deviceOs = response.deviceOs
                )
            )
            _library.value = null
            libraryRevision = null
            reconnectNow()
            PairingResult.Success
        } catch (e: Exception) {
            PairingResult.Failure(e.toUserMessage(noun))
        }
    }

    suspend fun forgetTv(notifyTv: Boolean = true) {
        val tv = settings.pairedTv.value
        connectJob?.cancel()
        if (tv != null && notifyTv) runCatching { withTimeoutOrNull(3_000) { client.unpair(tv) } }
        settings.clearPairedTv()
        _tvState.value = null
        _nowPlaying.value = null
        _library.value = null
        libraryRevision = null
        _connection.value = ConnectionState.NotPaired
    }

    // endregion

    // region commands

    suspend fun send(command: CompanionCommand): Boolean {
        val tv = settings.pairedTv.value ?: return false
        // optimistic local update so controls feel immediate.
        _nowPlaying.value?.let { live ->
            val now = SystemClock.elapsedRealtime()
            val current = live.positionAt(now)
            val updated = when (command) {
                CompanionCommand.TogglePlayback -> live.value.copy(isPlaying = !live.value.isPlaying, positionMs = current)
                is CompanionCommand.SeekTo -> live.value.copy(positionMs = command.positionMs)
                is CompanionCommand.SeekBy -> live.value.copy(positionMs = (current + command.deltaMs).coerceAtLeast(0))
                else -> null
            }
            if (updated != null) _nowPlaying.value = LiveNowPlaying(updated, now)
        }
        return runCatching { client.command(tv, command) }.isSuccess
    }

    // endregion

    private fun Exception.toUserMessage(noun: String): String = also { android.util.Log.w("TvRepository", "Pairing failed", it) }.let { when (this) {
        is TvApiException -> when (code) {
            "wrong_code" -> "That code didn't match. Check the code on your $noun."
            "pairing_closed" -> "Pairing isn't open on the $noun. Open Settings › Paired phones on the $noun and try again."
            "slow_down" -> "Please wait a moment and try again."
            else -> "The $noun refused the request ($status)."
        }
        else -> "Couldn't reach the $noun. Make sure Aperture is open and both devices are on the same network."
    } }

    companion object {
        /** For text about a device before the phone knows which kind it is. */
        const val UNKNOWN_NOUN = "TV or computer"

        private val BACKOFF_SECONDS = intArrayOf(2, 3, 5, 8, 13, 20)
    }
}

/** prefix of a synced palette id carrying another phone's colours: `phone:<p>,<s>,<t>,<i>`. */
const val PHONE_PALETTE_PREFIX = "phone:"

/**
 * what Match TV should follow. when the TV matches this phone, that's simply Material You;
 * when it matches another phone, its colours travel in the id.
 */
fun syncedThemeId(state: TvState, deviceId: String): String {
    if (state.themeId != PhoneThemeId) return state.themeId
    if (state.themeDeviceId == deviceId) return ThemeChoice.MATERIAL_YOU
    val c = state.themeColors ?: return state.themeId
    return PHONE_PALETTE_PREFIX + listOf(c.primary, c.secondary, c.tertiary, c.inversePrimary).joinToString(",")
}

fun parsePhonePalette(paletteId: String): PhoneColors? {
    if (!paletteId.startsWith(PHONE_PALETTE_PREFIX)) return null
    val v = paletteId.removePrefix(PHONE_PALETTE_PREFIX).split(",").mapNotNull { it.toIntOrNull() }
    return if (v.size == 4) PhoneColors(v[0], v[1], v[2], v[3]) else null
}
