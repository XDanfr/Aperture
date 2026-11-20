package me.xdan.aperture.data.jellyfin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.xdan.aperture.data.local.dao.JellyfinDao
import me.xdan.aperture.data.local.entity.JellyfinAccountEntity
import me.xdan.aperture.data.local.entity.RemoteItemEntity
import kotlinx.coroutines.flow.first
import me.xdan.aperture.platform.Log
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentApi
import org.jellyfin.sdk.model.api.MediaSegmentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.booleanOrNull
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.UUID

/**
 * Plays Jellyfin items: asks the server how (direct play, direct stream or a transcode) for this
 * device, and reports start, progress and stop so the server's resume points and Continue
 * Watching stay right.
 */
class JellyfinPlayback(
    private val clients: JellyfinClients,
    private val dao: JellyfinDao,
    /** The active profile's account; [JellyfinAccounts.activeAccount] in the app. */
    private val activeAccountFlow: kotlinx.coroutines.flow.Flow<JellyfinAccountEntity?>
) {
    /** Everything the player needs to start an item, and to report on it afterwards. */
    data class Stream(
        val account: JellyfinAccountEntity,
        val itemId: String,
        val mediaSourceId: String,
        val playSessionId: String?,
        val url: String,
        val method: PlayMethod,
        /** Text subtitles the server delivers as separate files, for the player to side-load. */
        val subtitles: List<ServerSubtitle>,
        /** Where to start, from the server's resume point unless starting over. */
        val startPositionMs: Long,
        val durationMs: Long?
    )

    data class ServerSubtitle(val url: String, val mimeType: String, val label: String, val language: String?, val isDefault: Boolean)

    enum class SegmentKind(val label: String, val autoSkippable: Boolean) {
        INTRO("Skip Intro", true), RECAP("Skip Recap", true), PREVIEW("Skip Preview", true),
        COMMERCIAL("Skip Ad", true), OUTRO("Skip Credits", false)
    }

    data class Segment(val kind: SegmentKind, val startMs: Long, val endMs: Long)

    /**
     * The item's skippable parts, as Intro Skipper (or any segment provider) marked them. Uses the
     * server's media segments, then the plugin's own endpoint for older plugin versions. Empty when
     * there are none or the server can't say.
     */
    suspend fun segments(account: JellyfinAccountEntity, itemId: String): List<Segment> = withContext(Dispatchers.IO) {
        val api = clients.forAccount(account) ?: return@withContext emptyList()
        val id = itemId.toUUIDOrNull() ?: return@withContext emptyList()
        val fromServer = runCatching {
            api.mediaSegmentApi.getItemSegments(id).content.items.mapNotNull { dto ->
                val kind = when (dto.type) {
                    MediaSegmentType.INTRO -> SegmentKind.INTRO
                    MediaSegmentType.RECAP -> SegmentKind.RECAP
                    MediaSegmentType.PREVIEW -> SegmentKind.PREVIEW
                    MediaSegmentType.COMMERCIAL -> SegmentKind.COMMERCIAL
                    MediaSegmentType.OUTRO -> SegmentKind.OUTRO
                    else -> null
                } ?: return@mapNotNull null
                Segment(kind, dto.startTicks / 10_000, dto.endTicks / 10_000)
            }
        }.onFailure { Log.w(TAG, "Media segments failed: ${it.message}") }.getOrDefault(emptyList())
        fromServer.ifEmpty { introSkipperSegments(api, itemId) }
            .filter { it.endMs - it.startMs >= 1_000 }
            .sortedBy { it.startMs }
    }

    /** Intro Skipper's own endpoint: {"Introduction": {"Start": s, "End": s, "Valid": true}, "Credits": …}. */
    private fun introSkipperSegments(api: ApiClient, itemId: String): List<Segment> = runCatching {
        val base = api.baseUrl?.trimEnd('/') ?: return emptyList()
        val token = api.accessToken ?: return emptyList()
        val connection = java.net.URL("$base/Episode/$itemId/IntroSkipperSegments").openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("Authorization", "MediaBrowser Token=\"$token\"")
        if (connection.responseCode != 200) return emptyList()
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        parseIntroSkipperSegments(body)
    }.getOrDefault(emptyList())

    suspend fun activeAccount(): JellyfinAccountEntity? = activeAccountFlow.first()

    suspend fun item(account: JellyfinAccountEntity, itemId: String): RemoteItemEntity? =
        withContext(Dispatchers.IO) { dao.item(account.id, itemId) }

    /** The episode after [episode] in the index, by season then number; specials are skipped. */
    suspend fun nextEpisode(account: JellyfinAccountEntity, episode: RemoteItemEntity): RemoteItemEntity? {
        val seriesId = episode.seriesId ?: return null
        val season = episode.seasonNumber ?: return null
        val number = episode.episodeNumber ?: return null
        return withContext(Dispatchers.IO) { dao.episodesOf(account.id, seriesId) }
            .filter { it.type == "EPISODE" && it.itemId != episode.itemId }
            .filter { (it.seasonNumber ?: 0) > 0 }
            .filter { other ->
                val s = other.seasonNumber ?: return@filter false
                val n = other.episodeNumber ?: return@filter false
                s > season || (s == season && n > number)
            }
            .minWithOrNull(compareBy({ it.seasonNumber }, { it.episodeNumber }))
    }

    /**
     * Null when the item can't be played here: unreachable server, or nothing the server can offer.
     * [allowDirect] false asks for a transcode, after the device failed to play the original.
     */
    suspend fun prepare(
        account: JellyfinAccountEntity,
        itemId: String,
        videoCodecs: Set<String>,
        startPositionMs: Long,
        allowDirect: Boolean = true
    ): Stream? = withContext(Dispatchers.IO) {
        val api = clients.forAccount(account) ?: return@withContext null
        val id = itemId.toUUIDOrNull() ?: return@withContext null
        val resumeMs = startPositionMs.coerceAtLeast(0)
        val info = runCatching {
            api.mediaInfoApi.getPostedPlaybackInfo(
                id,
                PlaybackInfoDto(
                    userId = account.userId.toUUIDOrNull(),
                    maxStreamingBitrate = MAX_BITRATE,
                    startTimeTicks = resumeMs * 10_000,
                    deviceProfile = deviceProfile(videoCodecs),
                    enableDirectPlay = allowDirect,
                    enableDirectStream = allowDirect,
                    enableTranscoding = true,
                    allowVideoStreamCopy = true,
                    allowAudioStreamCopy = true,
                    autoOpenLiveStream = false
                )
            ).content
        }.onFailure { Log.w(TAG, "PlaybackInfo failed: ${it.message}") }.getOrNull() ?: return@withContext null
        val source = info.mediaSources.firstOrNull() ?: return@withContext null
        val sourceId = source.id ?: itemId
        val (url, method) = streamUrl(api, itemId, source, info.playSessionId) ?: return@withContext null
        Stream(
            account = account,
            itemId = itemId,
            mediaSourceId = sourceId,
            playSessionId = info.playSessionId,
            url = url,
            method = method,
            subtitles = subtitles(api, source),
            startPositionMs = resumeMs,
            durationMs = source.runTimeTicks?.let { it / 10_000 }
        )
    }

    private fun streamUrl(api: ApiClient, itemId: String, source: MediaSourceInfo, playSessionId: String?): Pair<String, PlayMethod>? {
        val base = api.baseUrl?.trimEnd('/') ?: return null
        val token = api.accessToken ?: return null
        return when {
            // The file itself: the player reads the original container.
            source.supportsDirectPlay || source.supportsDirectStream -> {
                val params = listOfNotNull(
                    "static=true",
                    "mediaSourceId=${source.id ?: itemId}",
                    source.eTag?.let { "tag=$it" },
                    playSessionId?.let { "playSessionId=$it" },
                    "api_key=$token"
                ).joinToString("&")
                "$base/Videos/$itemId/stream?$params" to
                    if (source.supportsDirectPlay) PlayMethod.DIRECT_PLAY else PlayMethod.DIRECT_STREAM
            }
            source.supportsTranscoding && source.transcodingUrl != null -> {
                val path = source.transcodingUrl!!
                val withKey = if ("api_key=" in path || "ApiKey=" in path) path else "$path&api_key=$token"
                "$base$withKey" to PlayMethod.TRANSCODE
            }
            else -> null
        }
    }

    /** Text subtitles the server offers as files. Embedded ones play from the container itself. */
    private fun subtitles(api: ApiClient, source: MediaSourceInfo): List<ServerSubtitle> {
        val base = api.baseUrl?.trimEnd('/') ?: return emptyList()
        val token = api.accessToken ?: return emptyList()
        return source.mediaStreams.orEmpty()
            .filter { it.type == MediaStreamType.SUBTITLE && it.deliveryMethod == SubtitleDeliveryMethod.EXTERNAL && it.deliveryUrl != null }
            .map { stream ->
                val url = stream.deliveryUrl!!.let { if (it.startsWith("http")) it else base + it }
                ServerSubtitle(
                    url = if ("api_key=" in url) url else url + (if ('?' in url) "&" else "?") + "api_key=$token",
                    mimeType = when (stream.codec?.lowercase()) {
                        "ass", "ssa" -> "text/x-ssa"
                        "vtt", "webvtt" -> "text/vtt"
                        "ttml" -> "application/ttml+xml"
                        else -> "application/x-subrip"
                    },
                    label = stream.displayTitle ?: stream.language ?: "Subtitles",
                    language = stream.language,
                    isDefault = stream.index == source.defaultSubtitleStreamIndex
                )
            }
    }

    suspend fun reportStart(stream: Stream, positionMs: Long) = report(stream, "start") {
        sessionApi.reportPlaybackStart(
            PlaybackStartInfo(
                canSeek = true,
                itemId = stream.uuid,
                mediaSourceId = stream.mediaSourceId,
                playSessionId = stream.playSessionId,
                positionTicks = positionMs * 10_000,
                isPaused = false,
                isMuted = false,
                playMethod = stream.method,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT
            )
        )
    }

    suspend fun reportProgress(stream: Stream, positionMs: Long, paused: Boolean) = report(stream, "progress") {
        sessionApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                canSeek = true,
                itemId = stream.uuid,
                mediaSourceId = stream.mediaSourceId,
                playSessionId = stream.playSessionId,
                positionTicks = positionMs * 10_000,
                isPaused = paused,
                isMuted = false,
                playMethod = stream.method,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT
            )
        )
    }

    /**
     * Ends the session. The server decides watched state and the resume point from the position,
     * and the local index follows straight away so rows update without waiting for a sync.
     */
    suspend fun reportStopped(stream: Stream, positionMs: Long, durationMs: Long) {
        report(stream, "stop") {
            sessionApi.reportPlaybackStopped(
                PlaybackStopInfo(
                    itemId = stream.uuid,
                    mediaSourceId = stream.mediaSourceId,
                    playSessionId = stream.playSessionId,
                    positionTicks = positionMs * 10_000,
                    failed = false
                )
            )
        }
        val finished = durationMs > 0 && positionMs >= durationMs * 0.9
        withContext(Dispatchers.IO) {
            dao.updatePlayback(
                accountId = stream.account.id,
                itemId = stream.itemId,
                positionMs = if (finished) 0 else positionMs,
                played = finished,
                lastPlayedAt = System.currentTimeMillis()
            )
        }
    }

    /** Reports are best effort: playback carries on if the server misses one. */
    private suspend fun report(stream: Stream, what: String, call: suspend ApiClient.() -> Unit) = withContext(Dispatchers.IO) {
        val api = clients.forAccount(stream.account) ?: return@withContext
        runCatching { api.call() }.onFailure { Log.w(TAG, "Couldn't report playback $what: ${it.message}") }
        Unit
    }

    private val Stream.uuid: UUID get() = requireNotNull(itemId.toUUIDOrNull())

    private companion object {
        const val TAG = "JellyfinPlayback"
        /** Generous: the server only transcodes for bitrate when a client asks for less. */
        const val MAX_BITRATE = 120_000_000

        private val CONTAINERS = "mkv,mp4,m4v,mov,webm,ts,mpegts,m2ts,avi"
        private val AUDIO = "aac,mp3,mp2,ac3,eac3,truehd,dts,flac,opus,vorbis,alac,pcm_s16le,pcm_s24le"

        fun deviceProfile(videoCodecs: Set<String>) = DeviceProfile(
            name = "Aperture",
            maxStreamingBitrate = MAX_BITRATE,
            maxStaticBitrate = MAX_BITRATE,
            directPlayProfiles = listOf(
                DirectPlayProfile(
                    container = CONTAINERS,
                    type = DlnaProfileType.VIDEO,
                    videoCodec = videoCodecs.joinToString(","),
                    audioCodec = AUDIO
                )
            ),
            transcodingProfiles = listOf(
                TranscodingProfile(
                    container = "ts",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = listOf("hevc", "h264").filter { it in videoCodecs }.joinToString(",").ifEmpty { "h264" },
                    audioCodec = "aac,ac3,eac3,mp3",
                    protocol = MediaStreamProtocol.HLS,
                    conditions = emptyList(),
                    minSegments = 1,
                    breakOnNonKeyFrames = true
                )
            ),
            containerProfiles = emptyList(),
            codecProfiles = emptyList(),
            subtitleProfiles = listOf("srt", "subrip", "ass", "ssa", "vtt", "webvtt", "ttml").flatMap { format ->
                listOf(
                    SubtitleProfile(format = format, method = SubtitleDeliveryMethod.EMBED),
                    SubtitleProfile(format = format, method = SubtitleDeliveryMethod.EXTERNAL)
                )
            } + listOf("pgssub", "dvdsub", "dvbsub").map { SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EMBED) }
        )
    }
}

/** Parses Intro Skipper's segment map; seconds, with Start/End (newer) or IntroStart/IntroEnd (older). */
internal fun parseIntroSkipperSegments(body: String): List<JellyfinPlayback.Segment> =
    Json.parseToJsonElement(body).jsonObject.mapNotNull { (key, value) ->
        val kind = when (key.lowercase()) {
            "introduction", "intro" -> JellyfinPlayback.SegmentKind.INTRO
            "credits", "outro" -> JellyfinPlayback.SegmentKind.OUTRO
            "recap" -> JellyfinPlayback.SegmentKind.RECAP
            "preview" -> JellyfinPlayback.SegmentKind.PREVIEW
            "commercial" -> JellyfinPlayback.SegmentKind.COMMERCIAL
            else -> null
        } ?: return@mapNotNull null
        val fields = runCatching { value.jsonObject }.getOrNull() ?: return@mapNotNull null
        if (fields["Valid"]?.jsonPrimitive?.booleanOrNull == false) return@mapNotNull null
        val start = (fields["Start"] ?: fields["IntroStart"])?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val end = (fields["End"] ?: fields["IntroEnd"])?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        if (end <= start) null else JellyfinPlayback.Segment(kind, (start * 1000).toLong(), (end * 1000).toLong())
    }
