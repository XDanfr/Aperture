package me.xdan.aperture.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import me.xdan.aperture.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val state: StateFlow<UpdateCheckState> = _state

    suspend fun checkForUpdates(silent: Boolean = false) {
        if (_state.value is UpdateCheckState.Checking) return
        _state.value = UpdateCheckState.Checking
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/XDanfr/Aperture/releases?per_page=20")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Aperture/${BuildConfig.VERSION_NAME}")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "GitHub returned ${response.code}" }
                    val releases = JSONArray(response.body?.string().orEmpty())
                    val release = (0 until releases.length())
                        .asSequence()
                        .mapNotNull(releases::optJSONObject)
                        .firstOrNull(::isPublicApertureRelease)
                        ?: error("No Aperture release was found")
                    release.toUpdateState()
                }
            }.getOrElse { UpdateCheckState.Error(it.message ?: "Could not check for updates") }
        }
        _state.value = if (silent && result is UpdateCheckState.Error) {
            UpdateCheckState.Idle
        } else {
            result
        }
    }

    suspend fun downloadAndInstall(update: UpdateCheckState.Available) {
        val apkUrl = update.apkUrl
        if (apkUrl == null) {
            openUri(update.releaseUrl)
            return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateCheckState.PermissionRequired(update)
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }.onFailure {
                _state.value = UpdateCheckState.Error(
                    "Open Settings and allow Aperture to install unknown apps."
                )
            }
            return
        }

        runCatching {
            val downloadManager = context.getSystemService(DownloadManager::class.java)
            val fileName = "Aperture-${update.version}-${System.currentTimeMillis()}.apk"
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Aperture ${update.version}")
                .setDescription("Downloading update")
                .setMimeType(APK_MIME_TYPE)
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
            val downloadId = downloadManager.enqueue(request)
            _state.value = UpdateCheckState.Downloading(update, 0f)

            while (true) {
                delay(700)
                val status = downloadManager.query(
                    DownloadManager.Query().setFilterById(downloadId)
                ).use { cursor ->
                    check(cursor.moveToFirst()) { "The update download disappeared" }
                    val downloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    if (total > 0) {
                        _state.value = UpdateCheckState.Downloading(
                            update,
                            downloaded.toFloat() / total
                        )
                    }
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                }
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> break
                    DownloadManager.STATUS_FAILED -> error("The APK download failed")
                }
            }

            val apkFile = java.io.File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            check(apkFile.exists()) { "Downloaded APK could not be found" }
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            _state.value = UpdateCheckState.Installing(update.version)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apkUri, APK_MIME_TYPE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }.onFailure {
            _state.value = UpdateCheckState.Error(it.message ?: "Could not install the update")
        }
    }

    fun reset() {
        _state.value = UpdateCheckState.Idle
    }

    private fun isPublicApertureRelease(release: JSONObject): Boolean {
        if (release.optBoolean("draft", false)) return false
        return VERSION_TAG.matches(release.optString("tag_name"))
    }

    private fun JSONObject.toUpdateState(): UpdateCheckState {
        val latest = getString("tag_name")
        val releaseUrl = getString("html_url")
        val assets = optJSONArray("assets")
        val assetObjects = (0 until (assets?.length() ?: 0))
            .mapNotNull { assets?.optJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
        val preferredAsset = assetObjects.firstOrNull {
            it.optString("name").startsWith("Aperture-", ignoreCase = true) &&
                !it.optString("name").contains("debug", ignoreCase = true)
        } ?: assetObjects.firstOrNull {
            !it.optString("name").contains("debug", ignoreCase = true)
        }
        val apkUrl = preferredAsset
            ?.optString("browser_download_url")
            ?.takeIf(String::isNotBlank)
        return if (isNewer(latest, BuildConfig.VERSION_NAME)) {
            UpdateCheckState.Available(latest, releaseUrl, apkUrl)
        } else {
            UpdateCheckState.Current(latest)
        }
    }

    private fun openUri(uri: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val remoteVersion = ParsedVersion.parse(remote) ?: return false
        val localVersion = ParsedVersion.parse(local) ?: return false
        return remoteVersion > localVersion
    }

    private companion object {
        val VERSION_TAG = Regex(
            "^v?\\d+\\.\\d+(?:\\.\\d+)?(?:-(?:pre-)?(?:alpha|beta|rc)(?:\\.\\d+)?)?$",
            RegexOption.IGNORE_CASE
        )
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

private data class ParsedVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val channelRank: Int,
    val channelRevision: Int
) : Comparable<ParsedVersion> {
    override fun compareTo(other: ParsedVersion): Int = compareValuesBy(
        this,
        other,
        ParsedVersion::major,
        ParsedVersion::minor,
        ParsedVersion::patch,
        ParsedVersion::channelRank,
        ParsedVersion::channelRevision
    )

    companion object {
        fun parse(value: String): ParsedVersion? {
            val match = VERSION_PATTERN.matchEntire(value.trim()) ?: return null
            val channel = match.groupValues[4].lowercase()
            return ParsedVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].ifBlank { "0" }.toInt(),
                channelRank = when (channel) {
                    "pre-alpha" -> 0
                    "alpha" -> 1
                    "beta" -> 2
                    "rc" -> 3
                    else -> 4
                },
                channelRevision = match.groupValues[5].ifBlank { "0" }.toInt()
            )
        }

        private val VERSION_PATTERN = Regex(
            "^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-((?:pre-)?(?:alpha|beta|rc))(?:\\.(\\d+))?)?$",
            RegexOption.IGNORE_CASE
        )
    }
}

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class Current(val version: String) : UpdateCheckState
    data class Available(
        val version: String,
        val releaseUrl: String,
        val apkUrl: String?
    ) : UpdateCheckState
    data class PermissionRequired(val update: Available) : UpdateCheckState
    data class Downloading(val update: Available, val progress: Float) : UpdateCheckState
    data class Installing(val version: String) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}
