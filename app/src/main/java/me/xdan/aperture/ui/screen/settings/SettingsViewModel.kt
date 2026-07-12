package me.xdan.aperture.ui.screen.settings

import android.content.Context
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.annotation.ExperimentalCoilApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import me.xdan.aperture.BuildConfig
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val spotlightSettings: StateFlow<SpotlightSettings> = combine(
        userPreferencesRepository.hideFinishedFromSpotlight,
        userPreferencesRepository.finishedSpotlightExclusionDays
    ) { hideFinished, exclusionDays ->
        SpotlightSettings(hideFinished, exclusionDays)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SpotlightSettings()
    )

    val themeId = userPreferencesRepository.themeId.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "purple"
    )
    val showPresentationMode = userPreferencesRepository.showPresentationMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "grouped"
    )
    val subtitleAppearance = combine(
        userPreferencesRepository.subtitleTextScale,
        userPreferencesRepository.subtitleColour,
        userPreferencesRepository.subtitleBackgroundOpacity
    ) { scale, colour, backgroundOpacity ->
        SubtitleAppearanceSettings(scale, colour, backgroundOpacity)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SubtitleAppearanceSettings()
    )
    val updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val hiddenMedia = repository.getHiddenMedia().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    fun forceRescan() {
        viewModelScope.launch {
            repository.scanLocalFiles()
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache() {
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
    }

    fun setHideFinishedFromSpotlight(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHideFinishedFromSpotlight(enabled)
        }
    }

    fun setFinishedSpotlightExclusionDays(days: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setFinishedSpotlightExclusionDays(days)
        }
    }

    fun setTheme(themeId: String) {
        viewModelScope.launch { userPreferencesRepository.setThemeId(themeId) }
    }

    fun setShowPresentationMode(mode: String) {
        viewModelScope.launch { userPreferencesRepository.setShowPresentationMode(mode) }
    }

    fun setSubtitleAppearance(settings: SubtitleAppearanceSettings) {
        viewModelScope.launch {
            userPreferencesRepository.setSubtitleTextScale(settings.textScale)
            userPreferencesRepository.setSubtitleColour(settings.colour)
            userPreferencesRepository.setSubtitleBackgroundOpacity(settings.backgroundOpacity)
        }
    }

    fun unhide(mediaId: Long) {
        viewModelScope.launch { repository.setHidden(mediaId, false) }
    }

    fun checkForUpdates() {
        if (updateState.value is UpdateCheckState.Checking) return
        updateState.value = UpdateCheckState.Checking
        viewModelScope.launch {
            updateState.value = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url("https://api.github.com/repos/XDanfr/Aperture/releases?per_page=1")
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Aperture/${BuildConfig.VERSION_NAME}")
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "GitHub returned ${response.code}" }
                        val release = JSONArray(response.body?.string().orEmpty()).getJSONObject(0)
                        val latest = release.getString("tag_name")
                        val url = release.getString("html_url")
                        val assets = release.optJSONArray("assets")
                        val apkUrl = (0 until (assets?.length() ?: 0))
                            .asSequence()
                            .mapNotNull { assets?.optJSONObject(it) }
                            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                            ?.optString("browser_download_url")
                            ?.takeIf { it.isNotBlank() }
                        if (isNewer(latest, BuildConfig.VERSION_NAME)) {
                            UpdateCheckState.Available(latest, url, apkUrl)
                        } else {
                            UpdateCheckState.Current(latest)
                        }
                    }
                }.getOrElse { UpdateCheckState.Error(it.message ?: "Could not check for updates") }
            }
        }
    }

    fun downloadAndInstall(update: UpdateCheckState.Available) {
        val apkUrl = update.apkUrl
        if (apkUrl == null) {
            openUri(update.releaseUrl)
            return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            updateState.value = UpdateCheckState.PermissionRequired(update)
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }.onFailure {
                updateState.value = UpdateCheckState.Error("Open Settings and allow Aperture to install unknown apps.")
            }
            return
        }

        viewModelScope.launch {
            runCatching {
                val downloadManager = context.getSystemService(DownloadManager::class.java)
                val fileName = "Aperture-${update.version}-${System.currentTimeMillis()}.apk"
                val request = DownloadManager.Request(Uri.parse(apkUrl))
                    .setTitle("Aperture ${update.version}")
                    .setDescription("Downloading update")
                    .setMimeType(APK_MIME_TYPE)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                val downloadId = downloadManager.enqueue(request)
                updateState.value = UpdateCheckState.Downloading(update, 0f)

                while (true) {
                    delay(700)
                    downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                        check(cursor.moveToFirst()) { "The update download disappeared" }
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        if (total > 0) updateState.value = UpdateCheckState.Downloading(update, downloaded.toFloat() / total)
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> return@use
                            DownloadManager.STATUS_FAILED -> error("The APK download failed")
                        }
                    }
                    val status = downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else -1
                    }
                    if (status == DownloadManager.STATUS_SUCCESSFUL) break
                }

                val apkFile = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                check(apkFile.exists()) { "Downloaded APK could not be found" }
                val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
                updateState.value = UpdateCheckState.Installing(update.version)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(apkUri, APK_MIME_TYPE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }.onFailure {
                updateState.value = UpdateCheckState.Error(it.message ?: "Could not install the update")
            }
        }
    }

    fun resumeUpdateAfterPermission(update: UpdateCheckState.Available) {
        downloadAndInstall(update)
    }

    private fun openUri(uri: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(value: String) = Regex("\\d+").findAll(value).take(3).map { it.value.toInt() }.toList()
        val a = parts(remote)
        val b = parts(local)
        return (0..2).firstNotNullOfOrNull { index ->
            val difference = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            difference.takeIf { it != 0 }
        }?.let { it > 0 } ?: false
    }
}

data class SubtitleAppearanceSettings(
    val textScale: Float = 1f,
    val colour: String = "white",
    val backgroundOpacity: Float = 0.55f
)

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class Current(val version: String) : UpdateCheckState
    data class Available(val version: String, val releaseUrl: String, val apkUrl: String?) : UpdateCheckState
    data class PermissionRequired(val update: Available) : UpdateCheckState
    data class Downloading(val update: Available, val progress: Float) : UpdateCheckState
    data class Installing(val version: String) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

data class SpotlightSettings(
    val hideFinishedFromSpotlight: Boolean = true,
    val exclusionDays: Int = 14
)
