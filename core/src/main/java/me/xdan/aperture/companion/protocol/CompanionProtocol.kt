package me.xdan.aperture.companion.protocol

import android.net.Uri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * wire format shared by the TV (server) and phone companion (client).
 * plain HTTP on the local network, found over mDNS. pairing swaps a short code for a token.
 * only metadata and controls travel here; video never leaves the TV.
 */
object CompanionProtocol {
    const val VERSION = 1
    const val SERVICE_TYPE = "_aperture._tcp"
    const val DEFAULT_PORT = 47_650
    const val PORT_ATTEMPTS = 8
    const val CODE_LENGTH = 6

    const val PATH_INFO = "/v1/info"
    const val PATH_PAIR_REQUEST = "/v1/pair/request"
    const val PATH_PAIR = "/v1/pair"
    const val PATH_UNPAIR = "/v1/unpair"
    const val PATH_LIBRARY = "/v1/library"
    const val PATH_STATE = "/v1/state"
    const val PATH_EVENTS = "/v1/events"
    const val PATH_COMMAND = "/v1/command"
    const val PATH_COLORS = "/v1/colors"

    const val TXT_TV_ID = "id"
    const val TXT_VERSION = "v"

    const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** `aperture://pair?h=<host>&p=<port>&c=<code>&id=<tvId>&n=<name>` */
    fun pairingUri(host: String, port: Int, code: String, tvId: String, tvName: String): String =
        Uri.Builder()
            .scheme("aperture")
            .authority("pair")
            .appendQueryParameter("h", host)
            .appendQueryParameter("p", port.toString())
            .appendQueryParameter("c", code)
            .appendQueryParameter("id", tvId)
            .appendQueryParameter("n", tvName)
            .build()
            .toString()

    fun parsePairingUri(raw: String): PairingInvite? {
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "aperture" || uri.authority != "pair") return null
        val host = uri.getQueryParameter("h")?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.getQueryParameter("p")?.toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
        val code = uri.getQueryParameter("c")?.takeIf { it.length == CODE_LENGTH && it.all(Char::isDigit) }
            ?: return null
        return PairingInvite(
            host = host,
            port = port,
            code = code,
            tvId = uri.getQueryParameter("id").orEmpty(),
            tvName = uri.getQueryParameter("n").orEmpty().ifBlank { "Aperture TV" }
        )
    }
}

data class PairingInvite(
    val host: String,
    val port: Int,
    val code: String,
    val tvId: String,
    val tvName: String
)

@Serializable
data class TvInfo(
    val tvId: String,
    val tvName: String,
    val protocol: Int = CompanionProtocol.VERSION,
    val appVersion: String = ""
)

@Serializable
data class PairRequest(
    val deviceId: String,
    val deviceName: String
)

@Serializable
data class PairBody(
    val code: String,
    val deviceId: String,
    val deviceName: String
)

@Serializable
data class PairResponse(
    val token: String,
    val tvId: String,
    val tvName: String
)

@Serializable
data class ErrorBody(val error: String)

@Serializable
enum class CompanionMediaType { MOVIE, TV_SHOW, EPISODE }

@Serializable
data class CompanionMedia(
    val id: Long,
    val type: CompanionMediaType,
    val title: String,
    val year: Int? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val stillPath: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeOverview: String? = null,
    val durationMs: Long? = null,
    val positionMs: Long? = null,
    val lastWatchedAt: Long? = null,
    val completed: Boolean = false,
    val isFavorite: Boolean = false,
    val dateAdded: Long = 0
)

@Serializable
data class LibrarySnapshot(
    val revision: Long,
    val items: List<CompanionMedia>
)

@Serializable
data class NowPlaying(
    val mediaId: Long,
    val title: String,
    val subtitle: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val isPlaying: Boolean,
    val isBuffering: Boolean = false,
    val positionMs: Long,
    val durationMs: Long,
    val hasNextEpisode: Boolean = false,
    /** wall-clock time the position was sampled, so the phone can interpolate smoothly. */
    val sampledAt: Long = System.currentTimeMillis()
)

/**
 * a phone's Material You accents (dark-theme roles, ARGB), reported so the TV can offer
 * a theme that matches it.
 */
@Serializable
data class PhoneColors(
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
    val inversePrimary: Int
)

@Serializable
data class TvState(
    val tvName: String,
    val themeId: String,
    val nowPlaying: NowPlaying? = null,
    val libraryRevision: Long = 0,
    /** when [themeId] is the phone theme: whose colours the TV is using, and the colours. */
    val themeDeviceId: String? = null,
    val themeColors: PhoneColors? = null
)

@Serializable
sealed interface CompanionCommand {
    @Serializable @SerialName("play_media")
    data class PlayMedia(val mediaId: Long, val fromStart: Boolean = false) : CompanionCommand

    @Serializable @SerialName("toggle")
    data object TogglePlayback : CompanionCommand

    @Serializable @SerialName("seek_to")
    data class SeekTo(val positionMs: Long) : CompanionCommand

    @Serializable @SerialName("seek_by")
    data class SeekBy(val deltaMs: Long) : CompanionCommand

    @Serializable @SerialName("next_episode")
    data object NextEpisode : CompanionCommand

    @Serializable @SerialName("stop")
    data object Stop : CompanionCommand

    @Serializable @SerialName("favorite")
    data class SetFavorite(val mediaId: Long, val favorite: Boolean) : CompanionCommand
}

fun tmdbImageUrl(path: String?, size: String = "w342"): String? =
    path?.takeIf { it.isNotBlank() }?.let { CompanionProtocol.TMDB_IMAGE_BASE_URL + size + it }
