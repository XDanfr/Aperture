package me.xdan.aperture

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import me.xdan.aperture.di.androidModule
import me.xdan.aperture.di.sharedModule
import me.xdan.aperture.platform.AppBuild
import me.xdan.aperture.platform.AppConfig
import me.xdan.aperture.platform.AppPlatform
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ApertureApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        AppBuild.install(
            AppConfig(
                platform = AppPlatform.AndroidTv,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                isDebug = BuildConfig.DEBUG,
                tmdbApiKey = BuildConfig.TMDB_API_KEY,
                openSubtitlesApiKey = BuildConfig.OPENSUBTITLES_API_KEY,
                sideloadUpdatesEnabled = BuildConfig.SIDELOAD_UPDATES_ENABLED,
                githubSponsorshipEnabled = BuildConfig.GITHUB_SPONSORSHIP_ENABLED,
                sponsorPromptsEnabled = BuildConfig.SPONSOR_PROMPTS_ENABLED,
            )
        )
        startKoin {
            androidContext(this@ApertureApplication)
            modules(sharedModule, androidModule)
        }
    }

    // AsyncImage uses the same loader as the artwork palette store.
    override fun newImageLoader(context: PlatformContext): ImageLoader = get()
}
