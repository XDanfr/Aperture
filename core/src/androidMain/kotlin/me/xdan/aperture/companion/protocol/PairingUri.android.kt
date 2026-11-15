package me.xdan.aperture.companion.protocol

import android.net.Uri

internal actual fun buildPairingUri(parameters: Map<String, String>): String =
    Uri.Builder()
        .scheme("aperture")
        .authority("pair")
        .apply { parameters.forEach { (key, value) -> appendQueryParameter(key, value) } }
        .build()
        .toString()

internal actual fun parsePairingQuery(raw: String): Map<String, String>? {
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
    if (uri.scheme != "aperture" || uri.authority != "pair") return null
    return uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
}
