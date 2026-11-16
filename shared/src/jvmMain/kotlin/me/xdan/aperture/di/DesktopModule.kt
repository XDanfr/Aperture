package me.xdan.aperture.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import coil3.PlatformContext
import kotlinx.coroutines.Dispatchers
import me.xdan.aperture.companion.CompanionPlatform
import me.xdan.aperture.data.library.MediaLibrarySource
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.data.update.LegacyInstallMigration
import me.xdan.aperture.data.update.NoUpdateManager
import me.xdan.aperture.data.update.UpdateManager
import me.xdan.aperture.desktop.DesktopCompanionPlatform
import me.xdan.aperture.desktop.DesktopDirectories
import me.xdan.aperture.desktop.DesktopMediaLibrarySource
import me.xdan.aperture.desktop.DesktopPlaybackPlatform
import me.xdan.aperture.desktop.mpv.MpvPlayerEngine
import me.xdan.aperture.platform.KeyValueStores
import me.xdan.aperture.platform.PropertiesStores
import me.xdan.aperture.ui.screen.player.PlaybackPlatform
import me.xdan.aperture.ui.screen.player.PlayerEngine
import me.xdan.aperture.util.ScreenMetrics
import okio.Path.Companion.toOkioPath
import org.koin.dsl.bind
import org.koin.dsl.module
import java.awt.GraphicsEnvironment
import java.io.File

/** Desktop services behind the shared module's platform interfaces. */
val desktopModule = module {
    single<PlatformContext> { PlatformContext.INSTANCE }
    single<KeyValueStores> { PropertiesStores(File(DesktopDirectories.data, "preferences")) }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath {
            File(DesktopDirectories.data, "user_prefs.preferences_pb").toOkioPath()
        }
    }
    single<AppDatabase> {
        Room.databaseBuilder<AppDatabase>(File(DesktopDirectories.data, AppDatabase.DATABASE_NAME).absolutePath)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*me.xdan.aperture.data.local.DesktopMigrations.ALL_MIGRATIONS)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    single { ImageCacheDirectory(File(DesktopDirectories.cache, "image_cache")) }
    single<ScreenMetrics> {
        ScreenMetrics {
            // The main monitor in device pixels, as Android's display metrics report the TV panel.
            if (GraphicsEnvironment.isHeadless()) return@ScreenMetrics 1920 to 1080
            val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
            val mode = device.displayMode
            val scale = device.defaultConfiguration.defaultTransform.scaleX
            val width = mode.width.takeIf { it > 0 } ?: (device.defaultConfiguration.bounds.width * scale).toInt()
            val height = mode.height.takeIf { it > 0 } ?: (device.defaultConfiguration.bounds.height * scale).toInt()
            width to height
        }
    }
    single<MediaLibrarySource> { DesktopMediaLibrarySource() }
    single { MpvPlayerEngine() } bind PlayerEngine::class
    single { DesktopPlaybackPlatform() } bind PlaybackPlatform::class
    single<CompanionPlatform> { DesktopCompanionPlatform() }
    single<org.jellyfin.sdk.Jellyfin> {
        org.jellyfin.sdk.createJellyfin {
            clientInfo = org.jellyfin.sdk.model.ClientInfo(name = "Aperture", version = me.xdan.aperture.platform.AppBuild.VERSION_NAME)
        }
    }
    single<UpdateManager> { NoUpdateManager() }
    single<LegacyInstallMigration> { LegacyInstallMigration.None }
}
