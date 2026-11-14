package me.xdan.aperture.companion.protocol

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

// Matches android.net.Uri's encoding: spaces become %20, not '+'.
private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

internal actual fun buildPairingUri(parameters: Map<String, String>): String =
    "aperture://pair?" + parameters.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }

internal actual fun parsePairingQuery(raw: String): Map<String, String>? {
    val uri = runCatching { URI(raw) }.getOrNull() ?: return null
    if (uri.scheme != "aperture" || uri.authority != "pair") return null
    return uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate { part ->
        val key = part.substringBefore('=')
        val value = part.substringAfter('=', "")
        URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8)
    }
}
