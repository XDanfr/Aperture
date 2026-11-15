package me.xdan.aperture.platform


import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Build-time facts the shared app needs. Android fills this from BuildConfig (so product flavours
 * keep working); desktop fills it from its own build.
 */
data class AppConfig(
    val platform: AppPlatform,
    val versionName: String,
    val versionCode: Int,
    val isDebug: Boolean,
    val tmdbApiKey: String,
    val openSubtitlesApiKey: String,
    val sideloadUpdatesEnabled: Boolean,
    val githubSponsorshipEnabled: Boolean,
    val sponsorPromptsEnabled: Boolean,
)

enum class AppPlatform {
    AndroidTv,
    Desktop;

    val isDesktop: Boolean get() = this == Desktop
}

val LocalAppConfig = staticCompositionLocalOf<AppConfig> { error("AppConfig not provided") }

/**
 * Static access to [AppConfig], in the shape of Android's generated BuildConfig, for code that
 * read build flags directly. Installed once by each entry point before anything else runs.
 */
object AppBuild {
    private var installed: AppConfig? = null

    val config: AppConfig get() = checkNotNull(installed) { "AppBuild.install() was not called" }

    fun install(config: AppConfig) {
        installed = config
    }

    val VERSION_NAME: String get() = config.versionName
    val VERSION_CODE: Int get() = config.versionCode
    val DEBUG: Boolean get() = config.isDebug
    val TMDB_API_KEY: String get() = config.tmdbApiKey
    val OPENSUBTITLES_API_KEY: String get() = config.openSubtitlesApiKey
    val SIDELOAD_UPDATES_ENABLED: Boolean get() = config.sideloadUpdatesEnabled
    val GITHUB_SPONSORSHIP_ENABLED: Boolean get() = config.githubSponsorshipEnabled
    val SPONSOR_PROMPTS_ENABLED: Boolean get() = config.sponsorPromptsEnabled
    val PLATFORM: AppPlatform get() = config.platform
}
