package me.xdan.aperture.util

private val SecretQueryParam = Regex("(?i)([?&](?:api_key|apikey|access_token|token)=)[^&#]*")

/**
 * A URL that's safe to log: Jellyfin stream links carry the access token as `api_key`, and system
 * logs end up in bug reports.
 */
fun redactUrl(url: String): String = SecretQueryParam.replace(url) { it.groupValues[1] + "…" }
