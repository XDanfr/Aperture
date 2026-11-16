package me.xdan.aperture.companion.protocol

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
    const val TXT_DEVICE_KIND = "k"
    /** The server's operating system ([DeviceOs]); older servers don't send it. */
    const val TXT_DEVICE_OS = "o"

    const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** `aperture://pair?h=<host>&p=<port>&c=<code>&id=<tvId>&n=<name>&k=<deviceKind>` */
    fun pairingUri(
        host: String,
        port: Int,
        code: String,
        tvId: String,
        tvName: String,
        deviceKind: String = DeviceKind.TV,
        deviceOs: String = ""
    ): String =
        buildPairingUri(
            linkedMapOf(
                "h" to host, "p" to port.toString(), "c" to code, "id" to tvId, "n" to tvName, "k" to deviceKind, "o" to deviceOs
            )
        )

    fun parsePairingUri(raw: String): PairingInvite? {
        val query = parsePairingQuery(raw.trim()) ?: return null
        val host = query["h"]?.takeIf { it.isNotBlank() } ?: return null
        val port = query["p"]?.toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
        val code = query["c"]?.takeIf { it.length == CODE_LENGTH && it.all(Char::isDigit) }
            ?: return null
        return PairingInvite(
            host = host,
            port = port,
            code = code,
            tvId = query["id"].orEmpty(),
            tvName = query["n"].orEmpty().ifBlank { "Aperture TV" },
            deviceKind = query["k"].orEmpty().ifBlank { DeviceKind.TV },
            deviceOs = query["o"].orEmpty()
        )
    }
}

/** Builds `aperture://pair?…` with each parameter encoded the platform's usual way. */
internal expect fun buildPairingUri(parameters: Map<String, String>): String

/** Query parameters of an `aperture://pair` URI, or null when [raw] is anything else. */
internal expect fun parsePairingQuery(raw: String): Map<String, String>?

data class PairingInvite(
    val host: String,
    val port: Int,
    val code: String,
    val tvId: String,
    val tvName: String,
    val deviceKind: String = DeviceKind.TV,
    val deviceOs: String = ""
)

/**
 * What kind of device runs the Aperture server, so the phone can show a fitting icon. Sent as a
 * plain string: an older phone ignores it, and a newer kind falls back to [TV] on older phones.
 */
object DeviceKind {
    const val TV = "tv"
    const val DESKTOP = "desktop"
    const val LAPTOP = "laptop"

    /**
     * What to call a server in text: "TV" for a TV, "Mac" or "PC" for those computers, and
     * "computer" when the operating system isn't known (Linux, or an older version of Aperture).
     */
    fun noun(kind: String, os: String): String = when {
        kind == TV -> "TV"
        os == DeviceOs.MAC -> "Mac"
        os == DeviceOs.WINDOWS -> "PC"
        else -> "computer"
    }
}

/** The server's operating system, sent beside [DeviceKind] so the phone can say "Mac" or "PC". */
object DeviceOs {
    const val ANDROID = "android"
    const val MAC = "mac"
    const val WINDOWS = "windows"
    const val LINUX = "linux"
}

@Serializable
data class TvInfo(
    val tvId: String,
    val tvName: String,
    val protocol: Int = CompanionProtocol.VERSION,
    val appVersion: String = "",
    val deviceKind: String = DeviceKind.TV,
    val deviceOs: String = ""
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
    val tvName: String,
    val deviceKind: String = DeviceKind.TV,
    val deviceOs: String = ""
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
    val dateAdded: Long = 0,
    /**
     * Where the title is: [MediaSource.LOCAL] on the TV, or [MediaSource.JELLYFIN] on its server.
     * Jellyfin titles have negative ids, and their artwork paths are full image URLs.
     */
    val source: String = MediaSource.LOCAL
)

object MediaSource {
    const val LOCAL = "local"
    const val JELLYFIN = "jellyfin"
    const val BOTH = "both"
}

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
    val themeColors: PhoneColors? = null,
    val deviceKind: String = DeviceKind.TV,
    val deviceOs: String = "",
    /** who's watching on the TV; favourites and progress the phone sees are this profile's. */
    val profileName: String? = null
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

/** A TMDB path at [size], or a Jellyfin title's artwork, which arrives as a full URL. */
fun tmdbImageUrl(path: String?, size: String = "w342"): String? =
    path?.takeIf { it.isNotBlank() }?.let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else CompanionProtocol.TMDB_IMAGE_BASE_URL + size + it
    }
