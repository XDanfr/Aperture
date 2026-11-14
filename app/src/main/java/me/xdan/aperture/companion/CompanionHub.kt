package me.xdan.aperture.companion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.xdan.aperture.BuildConfig
import me.xdan.aperture.companion.protocol.CompanionCommand
import me.xdan.aperture.companion.protocol.CompanionMedia
import me.xdan.aperture.companion.protocol.CompanionMediaType
import me.xdan.aperture.companion.protocol.CompanionProtocol
import me.xdan.aperture.companion.protocol.PhoneColors
import me.xdan.aperture.ui.theme.PhoneThemeId
import me.xdan.aperture.companion.protocol.ErrorBody
import me.xdan.aperture.companion.protocol.LibrarySnapshot
import me.xdan.aperture.companion.protocol.NowPlaying
import me.xdan.aperture.companion.protocol.PairBody
import me.xdan.aperture.companion.protocol.PairRequest
import me.xdan.aperture.companion.protocol.PairResponse
import me.xdan.aperture.companion.protocol.TvInfo
import me.xdan.aperture.companion.protocol.TvState
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import me.xdan.aperture.ui.screen.player.PlayerEngine
import java.net.Inet4Address
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** a pairing window. the code is shown on the TV; phones exchange it for a token. */
data class PairingSession(
    val code: String,
    val expiresAt: Long,
    /** set when a phone asked to pair from its discovery list rather than via the TV's menu. */
    val requestedBy: String? = null,
    val pairedDeviceName: String? = null
)

/** commands for the active PlayerViewModel. */
sealed interface CompanionPlayerCommand {
    data object Toggle : CompanionPlayerCommand
    data class SeekTo(val positionMs: Long) : CompanionPlayerCommand
    data class SeekBy(val deltaMs: Long) : CompanionPlayerCommand
    data object NextEpisode : CompanionPlayerCommand
    data class Load(val mediaId: Long, val fromStart: Boolean) : CompanionPlayerCommand
}

/** navigation the activity performs for a phone. */
sealed interface CompanionNavigation {
    data class Play(val mediaId: Long, val fromStart: Boolean) : CompanionNavigation
    data object ExitPlayer : CompanionNavigation
}

sealed interface CompanionServerStatus {
    data object Stopped : CompanionServerStatus
    data object PermissionRequired : CompanionServerStatus
    data class Running(val host: String?, val port: Int) : CompanionServerStatus
}

/**
 * the TV end of the phone companion: runs the LAN server while Aperture is in the
 * foreground, advertises it over NSD, owns pairing and publishes playback state.
 */
@Singleton
class CompanionHub @Inject constructor(
    @ApplicationContext private val context: Context,
    private val devices: CompanionDeviceStore,
    private val mediaRepository: MediaRepository,
    private val preferences: UserPreferencesRepository,
    private val player: PlayerEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val random = SecureRandom()
    private val server = CompanionHttpServer(scope, ::handle)
    private var nsdListener: NsdManager.RegistrationListener? = null
    private var publishJob: Job? = null

    private val _status = MutableStateFlow<CompanionServerStatus>(CompanionServerStatus.Stopped)
    val status: StateFlow<CompanionServerStatus> = _status.asStateFlow()

    private val _pairing = MutableStateFlow<PairingSession?>(null)
    val pairing: StateFlow<PairingSession?> = _pairing.asStateFlow()
    private var failedAttempts = 0
    private var lastPairRequestAt = 0L

    private val _playerCommands = MutableSharedFlow<CompanionPlayerCommand>(extraBufferCapacity = 16)
    val playerCommands: SharedFlow<CompanionPlayerCommand> = _playerCommands.asSharedFlow()

    private val _navigation = MutableSharedFlow<CompanionNavigation>(extraBufferCapacity = 4)
    val navigation: SharedFlow<CompanionNavigation> = _navigation.asSharedFlow()

    val pairedPhones = devices.devices

    private val playerMedia = MutableStateFlow<MediaEntity?>(null)
    private val playerVisible = MutableStateFlow(false)
    /** the player's ViewModel outlives the player screen, so visibility gates what's published. */
    private val nowPlayingMedia = combine(playerMedia, playerVisible) { media, visible -> media.takeIf { visible } }
        .stateIn(scope, SharingStarted.Eagerly, null)
    private val hasNextEpisode = MutableStateFlow(false)
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)

    private val libraryRevision = combine(
        mediaRepository.getAllMedia(),
        mediaRepository.getAllProgress()
    ) { media, progress -> media.hashCode() * 31 + progress.hashCode() }
        .distinctUntilChanged()
        .debounce(500)
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /** the paired phone the "Match phone" theme follows, while it has reported colours. */
    val themePhone: StateFlow<PairedPhone?> = combine(devices.devices, devices.themePhoneId) { list, id ->
        list.firstOrNull { it.deviceId == id && it.colors != null }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun setThemePhone(deviceId: String) = devices.setThemePhone(deviceId)

    private val tvState: StateFlow<TvState> = combine(
        preferences.themeId,
        _nowPlaying,
        libraryRevision,
        themePhone
    ) { themeId, nowPlaying, revision, phone ->
        val matching = phone.takeIf { themeId == PhoneThemeId }
        TvState(devices.tvName, themeId, nowPlaying, revision.toLong(), matching?.deviceId, matching?.colors)
    }.stateIn(scope, SharingStarted.Eagerly, TvState(devices.tvName, "purple"))

    val hasLocalNetworkPermission: Boolean
        get() = Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
            context, LOCAL_NETWORK_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED

    // region lifecycle

    /** idempotent. called whenever the activity starts or permission is granted. */
    fun start() {
        if (_status.value is CompanionServerStatus.Running) return
        if (!hasLocalNetworkPermission) {
            _status.value = CompanionServerStatus.PermissionRequired
            return
        }
        val port = runCatching {
            server.start(CompanionProtocol.DEFAULT_PORT, CompanionProtocol.PORT_ATTEMPTS)
        }.getOrElse {
            Log.w(TAG, "Companion server failed to start", it)
            _status.value = CompanionServerStatus.Stopped
            return
        }
        _status.value = CompanionServerStatus.Running(localIpv4Address(), port)
        registerNsd(port)
        publishJob = scope.launch { trackPlayback() }
    }

    fun stop() {
        publishJob?.cancel()
        publishJob = null
        unregisterNsd()
        server.stop()
        _status.value = if (hasLocalNetworkPermission) CompanionServerStatus.Stopped
        else CompanionServerStatus.PermissionRequired
    }

    fun refreshAddress() {
        val running = _status.value as? CompanionServerStatus.Running ?: return
        _status.value = running.copy(host = localIpv4Address())
    }

    // endregion

    // region pairing

    fun openPairing(requestedBy: String? = null) {
        val current = _pairing.value
        // a code already on screen (menu dialog or an earlier request) stays as it is.
        if (current != null && current.pairedDeviceName == null && current.expiresAt > now()) return
        failedAttempts = 0
        _pairing.value = PairingSession(
            code = (0 until CompanionProtocol.CODE_LENGTH).joinToString("") { random.nextInt(10).toString() },
            expiresAt = now() + PAIRING_WINDOW_MS,
            requestedBy = requestedBy
        )
    }

    fun closePairing() {
        _pairing.value = null
    }

    fun unpair(deviceId: String) = devices.remove(deviceId)

    fun pairingUri(session: PairingSession): String? {
        val running = _status.value as? CompanionServerStatus.Running ?: return null
        val host = running.host ?: return null
        return CompanionProtocol.pairingUri(host, running.port, session.code, devices.tvId, devices.tvName)
    }

    // endregion

    // region playback publishing (called by PlayerViewModel)

    fun onPlaybackMedia(media: MediaEntity?) {
        playerMedia.value = media
        if (media == null) hasNextEpisode.value = false
    }

    /** called by the activity as the player screen enters or leaves the back stack. */
    fun onPlayerVisible(visible: Boolean) {
        playerVisible.value = visible
    }

    fun onNextEpisodeAvailable(available: Boolean) {
        hasNextEpisode.value = available
    }

    private suspend fun trackPlayback() {
        nowPlayingMedia.collectLatest { media ->
            if (media == null) {
                _nowPlaying.value = null
                return@collectLatest
            }
            while (true) {
                _nowPlaying.value = snapshot(media)
                // phones interpolate between samples; tighter updates only cost battery.
                delay(if (player.isPlaying.value) 1_000 else 500)
            }
        }
    }

    private fun snapshot(media: MediaEntity): NowPlaying {
        val isEpisode = media.type == "EPISODE"
        val subtitle = if (isEpisode) {
            listOfNotNull(
                if (media.seasonNumber != null && media.episodeNumber != null) "S${media.seasonNumber} · E${media.episodeNumber}" else null,
                media.episodeTitle
            ).joinToString(" · ").ifBlank { null }
        } else media.year?.toString()
        return NowPlaying(
            mediaId = media.id,
            title = media.title,
            subtitle = subtitle,
            posterPath = media.posterPath,
            backdropPath = (if (isEpisode) media.stillPath else null) ?: media.backdropPath,
            isPlaying = player.isPlaying.value,
            isBuffering = player.playbackState.value == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            hasNextEpisode = hasNextEpisode.value
        )
    }

    // endregion

    // region HTTP

    private suspend fun handle(request: CompanionHttpServer.Request, response: CompanionHttpServer.ResponseWriter) {
        val json = CompanionProtocol.json
        when (request.method to request.path) {
            "GET" to CompanionProtocol.PATH_INFO -> response.send(
                200, json.encodeToString(TvInfo.serializer(), TvInfo(devices.tvId, devices.tvName, appVersion = BuildConfig.VERSION_NAME))
            )
            "POST" to CompanionProtocol.PATH_PAIR_REQUEST -> {
                val body = runCatching { json.decodeFromString(PairRequest.serializer(), request.body) }.getOrNull()
                    ?: return response.error(400, "bad_request")
                val accepted = withContext(Dispatchers.Main) {
                    if (now() - lastPairRequestAt < 3_000) return@withContext false
                    lastPairRequestAt = now()
                    openPairing(requestedBy = body.deviceName.take(64))
                    true
                }
                if (accepted) response.send(204, "") else response.error(429, "slow_down")
            }
            "POST" to CompanionProtocol.PATH_PAIR -> {
                val body = runCatching { json.decodeFromString(PairBody.serializer(), request.body) }.getOrNull()
                    ?: return response.error(400, "bad_request")
                // pairing state lives on the main thread; socket writes must not.
                val (status, json) = withContext(Dispatchers.Main) { completePairing(body) }
                response.send(status, json)
            }
            else -> handleAuthenticated(request, response)
        }
    }

    private fun completePairing(body: PairBody): Pair<Int, String> {
        val session = _pairing.value
        if (session == null || session.pairedDeviceName != null || session.expiresAt < now()) {
            return 409 to errorJson("pairing_closed")
        }
        if (!MessageDigest.isEqual(session.code.toByteArray(), body.code.toByteArray())) {
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                // rotate the code after repeated guesses.
                _pairing.value = null
                openPairing(session.requestedBy)
            }
            return 403 to errorJson("wrong_code")
        }
        val name = body.deviceName.ifBlank { "Phone" }
        val token = devices.pair(body.deviceId, name)
        _pairing.value = session.copy(pairedDeviceName = name)
        return 200 to CompanionProtocol.json.encodeToString(PairResponse.serializer(), PairResponse(token, devices.tvId, devices.tvName))
    }

    private suspend fun handleAuthenticated(request: CompanionHttpServer.Request, response: CompanionHttpServer.ResponseWriter) {
        val device = devices.authenticate(request.bearerToken) ?: return response.error(401, "unauthorised")
        val json = CompanionProtocol.json
        when (request.method to request.path) {
            "GET" to CompanionProtocol.PATH_STATE -> response.send(200, json.encodeToString(TvState.serializer(), tvState.value))
            "GET" to CompanionProtocol.PATH_LIBRARY -> response.send(200, json.encodeToString(LibrarySnapshot.serializer(), librarySnapshot()))
            "GET" to CompanionProtocol.PATH_EVENTS -> streamEvents(response)
            "POST" to CompanionProtocol.PATH_COMMAND -> {
                val command = runCatching { json.decodeFromString(CompanionCommand.serializer(), request.body) }.getOrNull()
                    ?: return response.error(400, "bad_command")
                val accepted = withContext(Dispatchers.Main) { dispatch(command) }
                if (accepted) response.send(204, "") else response.error(409, "nothing_playing")
            }
            "POST" to CompanionProtocol.PATH_COLORS -> {
                val colors = runCatching { json.decodeFromString(PhoneColors.serializer(), request.body) }.getOrNull()
                    ?: return response.error(400, "bad_colors")
                devices.updateColors(device.deviceId, colors)
                response.send(204, "")
            }
            "POST" to CompanionProtocol.PATH_UNPAIR -> {
                devices.remove(device.deviceId)
                response.send(204, "")
            }
            else -> response.error(404, "not_found")
        }
    }

    private suspend fun streamEvents(response: CompanionHttpServer.ResponseWriter) {
        val stream = response.startEventStream()
        val json = CompanionProtocol.json
        // the collector ends (via IOException) when the phone disconnects or the server stops.
        kotlinx.coroutines.coroutineScope {
            val heartbeat = launch(Dispatchers.IO) {
                while (isActive) {
                    delay(15_000)
                    stream.heartbeat()
                }
            }
            try {
                tvState.collect { state ->
                    withContext(Dispatchers.IO) { stream.send("state", json.encodeToString(TvState.serializer(), state)) }
                }
            } finally {
                heartbeat.cancel()
            }
        }
    }

    private suspend fun dispatch(command: CompanionCommand): Boolean {
        val playing = nowPlayingMedia.value != null
        return when (command) {
            is CompanionCommand.PlayMedia -> {
                if (playing) _playerCommands.tryEmit(CompanionPlayerCommand.Load(command.mediaId, command.fromStart))
                else _navigation.tryEmit(CompanionNavigation.Play(command.mediaId, command.fromStart))
                true
            }
            CompanionCommand.TogglePlayback -> playing && _playerCommands.tryEmit(CompanionPlayerCommand.Toggle)
            is CompanionCommand.SeekTo -> playing && _playerCommands.tryEmit(CompanionPlayerCommand.SeekTo(command.positionMs))
            is CompanionCommand.SeekBy -> playing && _playerCommands.tryEmit(CompanionPlayerCommand.SeekBy(command.deltaMs))
            CompanionCommand.NextEpisode -> playing && _playerCommands.tryEmit(CompanionPlayerCommand.NextEpisode)
            CompanionCommand.Stop -> playing && _navigation.tryEmit(CompanionNavigation.ExitPlayer)
            is CompanionCommand.SetFavorite -> {
                mediaRepository.setFavorite(command.mediaId, command.favorite)
                true
            }
        }
    }

    private suspend fun librarySnapshot(): LibrarySnapshot {
        val media = mediaRepository.getAllMedia().first()
        val progress = mediaRepository.getAllProgress().first().associateBy { it.mediaId }
        val items = media.asSequence().filterNot { it.isHidden }.map { entity ->
            val p = progress[entity.id]
            CompanionMedia(
                id = entity.id,
                type = when (entity.type) {
                    "EPISODE" -> CompanionMediaType.EPISODE
                    "TV_SHOW" -> CompanionMediaType.TV_SHOW
                    else -> CompanionMediaType.MOVIE
                },
                title = entity.title,
                year = entity.year,
                overview = entity.overview,
                posterPath = entity.posterPath,
                backdropPath = entity.backdropPath,
                stillPath = entity.stillPath,
                seasonNumber = entity.seasonNumber,
                episodeNumber = entity.episodeNumber,
                episodeTitle = entity.episodeTitle,
                episodeOverview = entity.episodeOverview,
                durationMs = p?.duration?.takeIf { it > 0 } ?: entity.duration,
                positionMs = p?.position,
                lastWatchedAt = p?.lastUpdated,
                completed = p?.isCompleted == true,
                isFavorite = entity.isFavorite,
                dateAdded = entity.dateAdded
            )
        }.toList()
        return LibrarySnapshot(libraryRevision.value.toLong(), items)
    }

    private fun errorJson(code: String) = CompanionProtocol.json.encodeToString(ErrorBody.serializer(), ErrorBody(code))

    private fun CompanionHttpServer.ResponseWriter.error(status: Int, code: String) = send(status, errorJson(code))

    // endregion

    // region NSD and addressing

    private fun registerNsd(port: Int) {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "Aperture · ${devices.tvName}".take(63)
            serviceType = CompanionProtocol.SERVICE_TYPE
            setPort(port)
            setAttribute(CompanionProtocol.TXT_TV_ID, devices.tvId)
            setAttribute(CompanionProtocol.TXT_VERSION, CompanionProtocol.VERSION.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Advertised ${serviceInfo.serviceName} on $port")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onSuccess { nsdListener = listener }
            .onFailure { Log.w(TAG, "NSD unavailable", it) }
    }

    private fun unregisterNsd() {
        val listener = nsdListener ?: return
        nsdListener = null
        runCatching { context.getSystemService(NsdManager::class.java)?.unregisterService(listener) }
    }

    private fun localIpv4Address(): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = connectivity.activeNetwork ?: return null
        return connectivity.getLinkProperties(network)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    // endregion

    private fun now() = System.currentTimeMillis()

    companion object {
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
        private const val TAG = "CompanionHub"
        private const val PAIRING_WINDOW_MS = 10 * 60_000L
        private const val MAX_FAILED_ATTEMPTS = 5
    }
}
