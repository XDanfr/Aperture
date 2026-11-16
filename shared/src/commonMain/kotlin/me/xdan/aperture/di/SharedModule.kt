package me.xdan.aperture.di

import coil3.ImageLoader
import coil3.PlatformContext
import me.xdan.aperture.companion.CompanionDeviceStore
import me.xdan.aperture.companion.CompanionHub
import me.xdan.aperture.data.artwork.ArtworkPaletteInterceptor
import me.xdan.aperture.data.artwork.ArtworkPaletteStore
import me.xdan.aperture.data.artwork.ArtworkPrefetcher
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.data.remote.api.OpenSubtitlesApi
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.data.repository.MediaRepositoryImpl
import me.xdan.aperture.data.repository.UserPreferencesRepositoryImpl
import me.xdan.aperture.data.repository.ProfileRepositoryImpl
import me.xdan.aperture.domain.repository.ProfileRepository
import me.xdan.aperture.data.sponsor.SponsorVerificationManager
import me.xdan.aperture.data.subtitles.OpenSubtitlesSessionManager
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import me.xdan.aperture.platform.AppBuild
import me.xdan.aperture.ui.MainViewModel
import me.xdan.aperture.ui.screen.actions.MediaActionsViewModel
import me.xdan.aperture.ui.screen.ambient.AmbientViewModel
import me.xdan.aperture.ui.screen.companion.CompanionViewModel
import me.xdan.aperture.ui.screen.details.MediaDetailsViewModel
import me.xdan.aperture.ui.screen.home.HomeViewModel
import me.xdan.aperture.ui.screen.library.LibraryViewModel
import me.xdan.aperture.ui.screen.mylist.MyListViewModel
import me.xdan.aperture.ui.screen.player.PlayerViewModel
import me.xdan.aperture.ui.screen.search.SearchViewModel
import me.xdan.aperture.ui.screen.settings.SettingsViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Everything the app shares across platforms. Each entry point adds a platform module that
 * provides: [PlatformContext], [me.xdan.aperture.platform.KeyValueStores], the `user_prefs`
 * DataStore, [AppDatabase], [me.xdan.aperture.data.library.MediaLibrarySource],
 * [me.xdan.aperture.ui.screen.player.PlayerEngine],
 * [me.xdan.aperture.ui.screen.player.PlaybackPlatform],
 * [me.xdan.aperture.companion.CompanionPlatform], [me.xdan.aperture.data.update.UpdateManager],
 * [me.xdan.aperture.data.update.LegacyInstallMigration] and [me.xdan.aperture.util.ScreenMetrics].
 */
val sharedModule = module {
    // network
    single {
        val logging = HttpLoggingInterceptor().apply {
            level = if (AppBuild.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Api-Key")
            redactHeader("Authorization")
        }
        OkHttpClient.Builder().addInterceptor(logging).build()
    }
    single<TmdbApi> {
        Retrofit.Builder()
            .baseUrl(TmdbApi.BASE_URL)
            .client(get())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(TmdbApi::class.java)
    }
    single<OpenSubtitlesApi> {
        Retrofit.Builder()
            .baseUrl(OpenSubtitlesApi.BASE_URL)
            .client(get())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(OpenSubtitlesApi::class.java)
    }

    // database
    single { get<AppDatabase>().mediaDao() }
    single { get<AppDatabase>().playbackProgressDao() }
    single { get<AppDatabase>().artworkPaletteDao() }
    single { get<AppDatabase>().profileDao() }
    single { get<AppDatabase>().jellyfinDao() }

    // artwork. Coil's singleton loader is this one, so AsyncImage and the palette store agree.
    single<ImageLoader> {
        lateinit var loader: ImageLoader
        loader = newApertureImageLoader(get<PlatformContext>(), getKoin().getOrNull()) {
            add(ArtworkPaletteInterceptor(get()) { loader })
        }
        loader
    }
    single { ArtworkPrefetcher(get(), get()) { get() } }
    single { ArtworkPaletteStore(get(), get(), get()) { get() } }

    // repositories and services
    single<UserPreferencesRepository> { UserPreferencesRepositoryImpl(get()) }
    single<ProfileRepository> { ProfileRepositoryImpl(get(), get(), get(), get()) }

    // jellyfin. Each platform module provides the SDK's Jellyfin instance.
    single { me.xdan.aperture.data.jellyfin.JellyfinCredentials(get()) }
    single { me.xdan.aperture.data.jellyfin.JellyfinClients(get(), get(), get()) }
    single { me.xdan.aperture.data.jellyfin.JellyfinAccounts(get(), get(), get(), get()) }
    single { me.xdan.aperture.data.jellyfin.JellyfinSync(get(), get()) }
    single { me.xdan.aperture.data.jellyfin.JellyfinArtworkCache(get(), get(), get()) { get() } }
    single { me.xdan.aperture.data.jellyfin.JellyfinLibraryWatcher(get(), get(), get(), get(), get()) }
    single { me.xdan.aperture.data.jellyfin.JellyfinCatalog(get(), get()) }
    single { me.xdan.aperture.data.jellyfin.JellyfinPlayback(get(), get(), get<me.xdan.aperture.data.jellyfin.JellyfinAccounts>().activeAccount) }
    single<MediaRepository> { MediaRepositoryImpl(get(), get(), get(), get(), get(), get(), get()) }
    single { SponsorVerificationManager(get(), get()) }
    single { OpenSubtitlesSessionManager(get(), get()) }
    single { CompanionDeviceStore(get(), get()) }
    single { CompanionHub(get(), get(), get(), get(), get(), get()) }

    // view models
    viewModel { MainViewModel(get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) { get() } }
    viewModel { MyListViewModel(get(), get(), get(), get()) }
    viewModel { AmbientViewModel(get(), get(), get()) }
    viewModel { MediaDetailsViewModel(get(), get(), get()) }
    viewModel { SearchViewModel(get(), get(), get(), get()) }
    viewModel { LibraryViewModel(get(), get(), get(), get(), get()) }
    viewModel { MediaActionsViewModel(get()) }
    viewModel { PlayerViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { CompanionViewModel(get()) }
    viewModel { me.xdan.aperture.ui.screen.profiles.ProfilesViewModel(get()) }
    viewModel { me.xdan.aperture.ui.screen.jellyfin.JellyfinViewModel(get(), get(), get(), get(), get(), get()) }
    single { me.xdan.aperture.domain.catalog.LibraryQueryStore(get()) }
    single { me.xdan.aperture.domain.sidebar.SidebarRepository(get(), get()) }
    single { me.xdan.aperture.domain.sidebar.HomeRowsRepository(get(), get()) }
    single { me.xdan.aperture.domain.hybrid.HybridIndexSource(get(), get(), get(), get()) }
    viewModel { params -> me.xdan.aperture.ui.screen.jellyfin.JellyfinLibraryViewModel(params.get(), get(), get(), get()) }
    viewModel { me.xdan.aperture.ui.screen.jellyfin.JellyfinHomeViewModel(get(), get(), get()) }
    viewModel { params -> me.xdan.aperture.ui.screen.jellyfin.JellyfinDetailsViewModel(params.get(), get()) }
}

/** Platform-specific image loader setup (disk cache location, decoders). */
internal expect fun newApertureImageLoader(
    context: PlatformContext,
    diskCacheDirectory: ImageCacheDirectory?,
    components: coil3.ComponentRegistry.Builder.() -> Unit
): ImageLoader

/** Where desktop keeps Coil's disk cache; Android keeps Coil's default (cacheDir/image_cache). */
class ImageCacheDirectory(val path: java.io.File)
