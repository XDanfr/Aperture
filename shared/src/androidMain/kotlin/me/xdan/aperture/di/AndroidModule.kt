package me.xdan.aperture.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import coil3.PlatformContext
import me.xdan.aperture.companion.AndroidCompanionPlatform
import me.xdan.aperture.companion.CompanionPlatform
import me.xdan.aperture.data.library.AndroidMediaLibrarySource
import me.xdan.aperture.data.library.MediaLibrarySource
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.data.local.DatabaseMigrations
import me.xdan.aperture.data.update.AndroidLegacyInstallMigration
import me.xdan.aperture.data.update.GitHubApkUpdateManager
import me.xdan.aperture.data.update.LegacyInstallMigration
import me.xdan.aperture.data.update.NoUpdateManager
import me.xdan.aperture.data.update.UpdateManager
import me.xdan.aperture.platform.AppBuild
import me.xdan.aperture.platform.KeyValueStores
import me.xdan.aperture.platform.SharedPreferencesStores
import me.xdan.aperture.ui.screen.player.AndroidPlaybackPlatform
import me.xdan.aperture.ui.screen.player.PlaybackPlatform
import me.xdan.aperture.ui.screen.player.PlayerEngine
import me.xdan.aperture.ui.screen.player.PlayerEngineFactory
import me.xdan.aperture.ui.screen.player.PlayerEngineManager
import me.xdan.aperture.util.ScreenMetrics
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The same preferences file the app has always used. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** Android services behind the shared module's platform interfaces. */
val androidModule = module {
    // PlatformContext is Coil's alias for Context, so androidContext() here would resolve this definition again.
    single<PlatformContext> { androidApplication() }
    single<KeyValueStores> { SharedPreferencesStores(androidContext()) }
    single<DataStore<Preferences>> { androidContext().dataStore }
    single<AppDatabase> {
        Room.databaseBuilder<AppDatabase>(androidContext(), AppDatabase.DATABASE_NAME)
            .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
            .build()
    }
    single<ScreenMetrics> {
        val context = androidContext()
        ScreenMetrics {
            val metrics = context.resources.displayMetrics
            metrics.widthPixels to metrics.heightPixels
        }
    }
    single<MediaLibrarySource> { AndroidMediaLibrarySource(androidContext()) }
    single { PlayerEngineFactory(androidContext(), get()) }
    single<PlayerEngine> { PlayerEngineManager(get(), get()) }
    single<PlaybackPlatform> { AndroidPlaybackPlatform(androidContext()) }
    single<CompanionPlatform> { AndroidCompanionPlatform(androidContext()) }
    single<org.jellyfin.sdk.Jellyfin> {
        org.jellyfin.sdk.createJellyfin {
            clientInfo = org.jellyfin.sdk.model.ClientInfo(name = "Aperture", version = AppBuild.VERSION_NAME)
            context = androidContext()
        }
    }
    single<UpdateManager> {
        if (AppBuild.SIDELOAD_UPDATES_ENABLED) GitHubApkUpdateManager(get(), androidContext()) else NoUpdateManager()
    }
    single<LegacyInstallMigration> { AndroidLegacyInstallMigration(androidContext()) }
}
