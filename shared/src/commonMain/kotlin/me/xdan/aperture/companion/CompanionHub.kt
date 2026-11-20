package me.xdan.aperture.companion

import me.xdan.aperture.platform.Log
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
import me.xdan.aperture.platform.AppBuild
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
import java.security.MessageDigest
import me.xdan.aperture.data.jellyfin.isDiscover
import kotlinx.coroutines.flow.flatMapLatest
import java.security.SecureRandom

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
    data object SkipSegment : CompanionPlayerCommand
    data class Load(val mediaId: Long, val fromStart: Boolean) : CompanionPlayerCommand
    data class LoadJellyfin(val itemId: String, val fromStart: Boolean) : CompanionPlayerCommand
}

/** navigation the activity performs for a phone. */
sealed interface CompanionNavigation {
    data class Play(val mediaId: Long, val fromStart: Boolean) : CompanionNavigation
    data class PlayJellyfin(val key: me.xdan.aperture.domain.model.MediaKey.Jellyfin, val fromStart: Boolean) : CompanionNavigation
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
class CompanionHub(
    private val platform: CompanionPlatform,
    private val devices: CompanionDeviceStore,
    private val mediaRepository: MediaRepository,
    private val preferences: UserPreferencesRepository,
    private val player: PlayerEngine,
    private val profiles: me.xdan.aperture.domain.repository.ProfileRepository,
    private val jellyfin: me.xdan.aperture.data.jellyfin.JellyfinCatalog,
    private val jellyfinDao: me.xdan.aperture.data.local.dao.JellyfinDao,
    private val hybrid: me.xdan.aperture.domain.hybrid.HybridIndexSource,
    private val sidebar: me.xdan.aperture.domain.sidebar.SidebarRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val random = SecureRandom()
    private val server = CompanionHttpServer(scope, ::handle)
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
    private val skipLabel = MutableStateFlow<String?>(null)
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)

    /** The active profile's server titles, so the revision moves when they (or their progress) change. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val serverTitles = combine(profiles.activeLibraryMode, jellyfin.activeAccount) { mode, account -> account?.takeIf { mode.usesJellyfin } }
        .flatMapLatest { account ->
            if (account == null) kotlinx.coroutines.flow.flowOf(0)
            else combine(jellyfinDao.itemsOfType(account.id, "MOVIE"), jellyfinDao.itemsOfType(account.id, "EPISODE")) { movies, episodes ->
                movies.hashCode() * 31 + episodes.hashCode()
            }
        }

    private val libraryRevision = combine(
        mediaRepository.getAllMedia(),
        mediaRepository.getAllProgress(),
        profiles.activeLibraryMode,
        serverTitles,
        // The phone's Library tab mirrors the sidebar's order and icons.
        sidebar.layout
    ) { media, progress, mode, server, layout ->
        (((media.hashCode() * 31 + progress.hashCode()) * 31 + mode.hashCode()) * 31 + server) * 31 + layout.hashCode()
    }
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
        themePhone,
        profiles.activeProfile
    ) { themeId, nowPlaying, revision, phone, profile ->
        val matching = phone.takeIf { themeId == PhoneThemeId }
        TvState(
            devices.tvName, themeId, nowPlaying, revision.toLong(), matching?.deviceId, matching?.colors,
            deviceKind = platform.deviceKind, deviceOs = platform.deviceOs, profileName = profile?.name,
            librarySource = when (profile?.libraryMode) {
                "JELLYFIN" -> me.xdan.aperture.companion.protocol.MediaSource.JELLYFIN
                "HYBRID" -> me.xdan.aperture.companion.protocol.MediaSource.BOTH
                else -> me.xdan.aperture.companion.protocol.MediaSource.LOCAL
            }
        )
    }.stateIn(scope, SharingStarted.Eagerly, TvState(devices.tvName, "purple", deviceKind = platform.deviceKind, deviceOs = platform.deviceOs))

    val hasLocalNetworkPermission: Boolean
        get() = platform.hasLocalNetworkPermission

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
        return CompanionProtocol.pairingUri(host, running.port, session.code, devices.tvId, devices.tvName, platform.deviceKind, platform.deviceOs)
    }

    // endregion

    // region playback publishing (called by PlayerViewModel)

    fun onPlaybackMedia(media: MediaEntity?) {
        playerMedia.value = media
        if (media == null) { hasNextEpisode.value = false; skipLabel.value = null }
    }

    /** called by the activity as the player screen enters or leaves the back stack. */
    fun onPlayerVisible(visible: Boolean) {
        playerVisible.value = visible
    }

    /** The Skip button's label while a Jellyfin segment plays, null otherwise. */
    fun onSkipLabelChanged(label: String?) {
        skipLabel.value = label
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
            isBuffering = player.playbackState.value == me.xdan.aperture.ui.screen.player.PlaybackStates.BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            hasNextEpisode = hasNextEpisode.value,
            skipLabel = skipLabel.value
        )
    }

    // endregion

    // region HTTP

    private suspend fun handle(request: CompanionHttpServer.Request, response: CompanionHttpServer.ResponseWriter) {
        val json = CompanionProtocol.json
        when (request.method to request.path) {
            "GET" to CompanionProtocol.PATH_INFO -> response.send(
                200, json.encodeToString(TvInfo.serializer(), TvInfo(devices.tvId, devices.tvName, appVersion = AppBuild.VERSION_NAME, deviceKind = platform.deviceKind, deviceOs = platform.deviceOs))
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
        return 200 to CompanionProtocol.json.encodeToString(PairResponse.serializer(), PairResponse(token, devices.tvId, devices.tvName, platform.deviceKind, platform.deviceOs))
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
                if (command.mediaId < 0) {
                    // A server title: its id maps back to the item.
                    val key = jellyfinKey(command.mediaId) ?: return false
                    if (playing) _playerCommands.tryEmit(CompanionPlayerCommand.LoadJellyfin(key.itemId, command.fromStart))
                    else _navigation.tryEmit(CompanionNavigation.PlayJellyfin(key, command.fromStart))
                    return true
                }
                if (playing) _playerCommands.tryEmit(CompanionPlayerCommand.Load(command.mediaId, command.fromStart))
                else _navigation.tryEmit(CompanionNavigation.Play(command.mediaId, command.fromStart))
                true
            }
            CompanionCommand.TogglePlayback -> playing && _playerCommands.tryEmit(CompanionPlayerCommand.Toggle)
            is CompanionCommand.SeekTo -> playing && _playerCommands.tryEmit(CompanionPlayerCommand.SeekTo(command.positionMs))
            is CompanionCommand.SeekBy -> playing && _playerCommands.tryEmit(CompanionPlayerCommand.SeekBy(command.deltaMs))
            CompanionCommand.NextEpisode -> playing && _playerCommands.tryEmit(CompanionPlayerCommand.NextEpisode)
            CompanionCommand.SkipSegment -> playing && _playerCommands.tryEmit(CompanionPlayerCommand.SkipSegment)
            CompanionCommand.Stop -> playing && _navigation.tryEmit(CompanionNavigation.ExitPlayer)
            is CompanionCommand.SetFavorite -> {
                if (command.mediaId < 0) {
                    val key = jellyfinKey(command.mediaId) ?: return false
                    val account = jellyfin.activeAccount.first() ?: return false
                    jellyfin.setFavorite(account, key.itemId, command.favorite)
                } else {
                    mediaRepository.setFavorite(command.mediaId, command.favorite)
                    true
                }
            }
        }
    }

    /** Server titles by their companion id, from the last snapshot (or looked up). */
    private val jellyfinIds = java.util.concurrent.ConcurrentHashMap<Long, me.xdan.aperture.domain.model.MediaKey.Jellyfin>()

    private suspend fun jellyfinKey(id: Long): me.xdan.aperture.domain.model.MediaKey.Jellyfin? {
        jellyfinIds[id]?.let { return it }
        val account = jellyfin.activeAccount.first() ?: return null
        var item: me.xdan.aperture.data.local.entity.RemoteItemEntity? = null
        for (type in listOf("MOVIE", "EPISODE", "SERIES")) {
            item = jellyfinDao.itemsOfType(account.id, type).first()
                .firstOrNull { me.xdan.aperture.ui.screen.player.PlayerViewModel.jellyfinDisplayId(it.itemId) == id }
            if (item != null) break
        }
        if (item == null) return null
        return me.xdan.aperture.domain.model.MediaKey.Jellyfin(account.id, item.itemId).also { jellyfinIds[id] = it }
    }

    /**
     * The active profile's server films and episodes, as the phone sees local ones. Discover's
     * requestable placeholders stay out, and in hybrid mode so do titles this device has too.
     */
    private suspend fun jellyfinSnapshot(): List<CompanionMedia> {
        if (!profiles.activeLibraryMode.first().usesJellyfin) return emptyList()
        val account = jellyfin.activeAccount.first() ?: return emptyList()
        val libraries = jellyfin.libraries.first().associateBy { it.id }
        val usesLocal = profiles.activeLibraryMode.first().usesLocal
        // Only items that really are on this device too: a show's server-only episodes stay.
        val hybridIndex = hybrid.index.first()
        val alsoLocal = hybridIndex.duplicateRemoteItemIds
        val images = me.xdan.aperture.domain.catalog.JellyfinImages(account.baseUrl)
        // The phone describes a show with its episodes' overview field, so episodes carry the show's.
        val showOverviews = jellyfinDao.itemsOfType(account.id, "SERIES").first().associate { it.itemId to it.overview }
        // Discover's shows are placeholders without episodes, so they go as single titles.
        val discoverShows = jellyfinDao.itemsOfType(account.id, "SERIES").first().filter { libraries[it.libraryId]?.isDiscover == true }
        return (listOf("MOVIE", "EPISODE").flatMap { jellyfinDao.itemsOfType(account.id, it).first() } + discoverShows)
            .filter { it.itemId !in alsoLocal }
            .map { entity ->
                val id = me.xdan.aperture.ui.screen.player.PlayerViewModel.jellyfinDisplayId(entity.itemId)
                jellyfinIds[id] = me.xdan.aperture.domain.model.MediaKey.Jellyfin(account.id, entity.itemId)
                val catalog = images.item(entity)
                val episode = entity.type == "EPISODE"
                CompanionMedia(
                    id = id,
                    type = when (entity.type) {
                        "EPISODE" -> CompanionMediaType.EPISODE
                        "SERIES" -> CompanionMediaType.TV_SHOW
                        else -> CompanionMediaType.MOVIE
                    },
                    // A show this device has too goes under its local title, so the phone lists it once.
                    title = if (episode) entity.seriesId?.let(hybridIndex.localTitleForSeries::get) ?: entity.seriesName ?: entity.name else entity.name,
                    year = entity.year,
                    overview = if (episode) entity.seriesId?.let(showOverviews::get) else entity.overview,
                    posterPath = images.titlePoster(entity) ?: catalog.poster,
                    backdropPath = catalog.backdrop,
                    stillPath = if (episode) catalog.thumb else null,
                    seasonNumber = entity.seasonNumber,
                    episodeNumber = entity.episodeNumber,
                    episodeTitle = if (episode) entity.name else null,
                    episodeOverview = if (episode) entity.overview else null,
                    durationMs = entity.runtimeMs,
                    positionMs = entity.playbackPositionMs.takeIf { it > 0 },
                    lastWatchedAt = entity.lastPlayedAt,
                    completed = entity.played,
                    isFavorite = entity.isFavorite,
                    dateAdded = entity.dateCreated ?: 0,
                    source = me.xdan.aperture.companion.protocol.MediaSource.JELLYFIN,
                    collection = entity.libraryId?.let { id -> collectionKey(libraries[id], id, usesLocal) }
                )
            }
    }

    /**
     * The collection a server library's titles go in: its own, except that in hybrid mode the
     * server's films and shows join this device's Movies and TV shows, as on the TV's sidebar.
     */
    private fun collectionKey(library: me.xdan.aperture.data.local.entity.RemoteLibraryEntity?, id: String, usesLocal: Boolean): String = when {
        usesLocal && library?.collectionType == "movies" -> LOCAL_MOVIES
        usesLocal && library?.collectionType == "tvshows" -> LOCAL_SHOWS
        else -> "library:$id"
    }

    /** The TV's library entries, in the profile's sidebar order with its icons. */
    private suspend fun collections(): List<me.xdan.aperture.companion.protocol.CompanionCollection> {
        val mode = profiles.activeLibraryMode.first()
        val candidates = buildList {
            if (mode.usesLocal) {
                add(me.xdan.aperture.companion.protocol.CompanionCollection(LOCAL_MOVIES, "Movies", me.xdan.aperture.companion.protocol.CollectionKind.MOVIES, "movie"))
                add(me.xdan.aperture.companion.protocol.CompanionCollection(LOCAL_SHOWS, "TV shows", me.xdan.aperture.companion.protocol.CollectionKind.SHOWS, "tv"))
            }
            if (mode.usesJellyfin && jellyfin.activeAccount.first() != null) {
                jellyfin.libraries.first()
                    .filter { it.collectionType in me.xdan.aperture.data.jellyfin.JellyfinSync.INDEXED_COLLECTIONS }
                    .filterNot { mode.usesLocal && it.collectionType in setOf("movies", "tvshows") }
                    .forEach { library ->
                        val (kind, icon) = when {
                            library.collectionType == "movies" -> me.xdan.aperture.companion.protocol.CollectionKind.MOVIES to "movie"
                            library.collectionType == "tvshows" -> me.xdan.aperture.companion.protocol.CollectionKind.SHOWS to "tv"
                            library.collectionType == "homevideos" -> me.xdan.aperture.companion.protocol.CollectionKind.HOME_VIDEOS to "videocam"
                            library.isDiscover -> me.xdan.aperture.companion.protocol.CollectionKind.DISCOVER to "explore"
                            else -> me.xdan.aperture.companion.protocol.CollectionKind.OTHER to "folder"
                        }
                        add(me.xdan.aperture.companion.protocol.CompanionCollection("library:${library.id}", library.name, kind, icon))
                    }
            }
        }
        val layout = sidebar.layout.first()
        val byKey = candidates.associateBy { it.key }
        return layout.arrange(candidates.map { it.key }).mapNotNull(byKey::get)
            .map { c -> layout.icons[c.key]?.let { c.copy(icon = it) } ?: c }
    }

    private suspend fun librarySnapshot(): LibrarySnapshot {
        val server = jellyfinSnapshot()
        val collections = collections()
        if (!profiles.activeLibraryMode.first().usesLocal) return LibrarySnapshot(libraryRevision.value.toLong(), server, collections)
        val media = mediaRepository.getAllMedia().first()
        val progress = mediaRepository.getAllProgress().first().associateBy { it.mediaId }
        val items = media.asSequence().filterNot { it.isHidden || it.missingSince != null }.map { entity ->
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
                dateAdded = entity.dateAdded,
                collection = if (entity.type == "EPISODE") LOCAL_SHOWS else LOCAL_MOVIES
            )
        }.toList()
        return LibrarySnapshot(libraryRevision.value.toLong(), items + server, collections)
    }

    private fun errorJson(code: String) = CompanionProtocol.json.encodeToString(ErrorBody.serializer(), ErrorBody(code))

    private fun CompanionHttpServer.ResponseWriter.error(status: Int, code: String) = send(status, errorJson(code))

    // endregion

    // region NSD and addressing

    private fun registerNsd(port: Int) {
        runCatching {
            platform.advertise(
                serviceName = "Aperture · ${devices.tvName}".take(63),
                port = port,
                txt = mapOf(
                    CompanionProtocol.TXT_TV_ID to devices.tvId,
                    CompanionProtocol.TXT_VERSION to CompanionProtocol.VERSION.toString(),
                    CompanionProtocol.TXT_DEVICE_KIND to platform.deviceKind,
                    CompanionProtocol.TXT_DEVICE_OS to platform.deviceOs
                )
            )
        }.onFailure { Log.w(TAG, "NSD unavailable", it) }
    }

    private fun unregisterNsd() {
        runCatching { platform.stopAdvertising() }
    }

    private fun localIpv4Address(): String? = platform.localIpv4Address()

    // endregion

    private fun now() = System.currentTimeMillis()

    companion object {
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
        private const val TAG = "CompanionHub"
        private const val PAIRING_WINDOW_MS = 10 * 60_000L
        private const val MAX_FAILED_ATTEMPTS = 5
        /** This device's grids, the same keys as the TV's sidebar entries. */
        private const val LOCAL_MOVIES = "movies"
        private const val LOCAL_SHOWS = "shows"
    }
}
