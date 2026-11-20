package me.xdan.aperture.mobile

import me.xdan.aperture.mobile.ui.components.LocalDeviceNoun
import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import me.xdan.aperture.mobile.ui.theme.brandPalette
import me.xdan.aperture.mobile.ui.theme.companionColorScheme
import me.xdan.aperture.mobile.ui.theme.splashPaletteIdFor
import me.xdan.aperture.mobile.ui.theme.splashThemeFor
import me.xdan.aperture.ui.theme.DefaultThemeId
import android.window.SplashScreenView
import androidx.compose.ui.geometry.Rect
import me.xdan.aperture.mobile.ui.screens.LaunchOverlay
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import me.xdan.aperture.mobile.data.ConnectionState
import me.xdan.aperture.mobile.data.LibraryEntry
import me.xdan.aperture.mobile.ui.CompanionViewModel
import me.xdan.aperture.mobile.ui.screens.DetailsSheet
import me.xdan.aperture.mobile.ui.screens.HomeScreen
import me.xdan.aperture.mobile.ui.screens.LibraryLoading
import me.xdan.aperture.mobile.ui.screens.LibraryScreen
import me.xdan.aperture.mobile.ui.screens.NotPairedPrompt
import me.xdan.aperture.mobile.ui.screens.NowPlayingBar
import me.xdan.aperture.mobile.ui.screens.NowPlayingSheet
import me.xdan.aperture.mobile.ui.screens.OnboardingScreen
import me.xdan.aperture.mobile.ui.screens.PairingPage
import me.xdan.aperture.mobile.ui.screens.SettingsScreen
import me.xdan.aperture.mobile.ui.screens.DiscoverScreen
import me.xdan.aperture.mobile.ui.theme.CompanionTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CompanionViewModel by viewModels()

    /** where the system splash drew the launcher mark (window px), once it hands over. */
    private var splashMark by mutableStateOf<Rect?>(null)
    private var splashView: SplashScreenView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // only a cold launch plays the brand animation; folding or rotating must not replay it.
        val playLaunch = savedInstanceState == null
        val splashPrefs = getSharedPreferences("splash", MODE_PRIVATE)
        // Android 13+ shows the splash style set on the previous run; earlier versions, purple.
        val shownSplashPaletteId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            splashPaletteIdFor(splashPrefs.getString(KEY_SPLASH_PALETTE, null))
        } else DefaultThemeId
        if (playLaunch && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { view ->
                splashView = view
                splashMark = view.iconView?.let { icon ->
                    val location = IntArray(2).also(icon::getLocationInWindow)
                    // see SPLASH_MARK_FRACTION and tools/generate_brand_assets.py.
                    val size = icon.width * SPLASH_MARK_FRACTION
                    val left = location[0] + (icon.width - size) / 2f
                    val top = location[1] + (icon.height - size) / 2f
                    Rect(left, top, left + size, top + size)
                } ?: Rect.Zero
            }
        }
        setContent {
            val paletteId by viewModel.paletteId.collectAsState()
            var launching by rememberSaveable { mutableStateOf(playLaunch) }
            // keep the next launch's system splash in the current theme's colours.
            LaunchedEffect(paletteId) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    splashPrefs.getString(KEY_SPLASH_PALETTE, null) != paletteId
                ) {
                    splashScreen.setSplashScreenTheme(splashThemeFor(paletteId))
                    splashPrefs.edit().putString(KEY_SPLASH_PALETTE, paletteId).apply()
                }
            }
            val pairedTv by viewModel.pairedTv.collectAsState()
            CompanionTheme(paletteId) {
              androidx.compose.runtime.CompositionLocalProvider(
                  me.xdan.aperture.mobile.ui.components.LocalDeviceNoun provides
                      (pairedTv?.noun ?: me.xdan.aperture.mobile.data.TvRepository.UNKNOWN_NOUN)
              ) {
                val onboarded by viewModel.onboardingDone.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                        if (!onboarded) OnboardingScreen(viewModel) else CompanionHome(viewModel)
                    }
                    if (launching) {
                        val context = LocalContext.current
                        val dark = isSystemInDarkTheme()
                        LaunchOverlay(
                            splashPalette = remember {
                                companionColorScheme(context, shownSplashPaletteId, dark).brandPalette()
                            },
                            palette = MaterialTheme.colorScheme.brandPalette(),
                            splashMark = { splashMark?.takeUnless { it == Rect.Zero } },
                            onFirstFrame = {
                                splashView?.remove()
                                splashView = null
                            },
                            onFinished = { launching = false }
                        )
                    }
                }
              }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        viewModel.onBackground()
        super.onStop()
    }
}

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    /** Only when the TV's server has a Discover library. */
    DISCOVER("Discover", Icons.Outlined.Explore, Icons.Rounded.Explore),
    LIBRARY("Library", Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings)
}

private const val KEY_SPLASH_PALETTE = "palette_id"

// how much of the splash icon the mark fills (checked on a fold5)
private const val SPLASH_MARK_FRACTION = 100f / 176f * 108f / 72f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CompanionHome(viewModel: CompanionViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var pairing by rememberSaveable { mutableStateOf(false) }
    var openEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var nowPlayingExpanded by rememberSaveable { mutableStateOf(false) }
    val connection by viewModel.connection.collectAsState()
    val library by viewModel.library.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val tvState by viewModel.tvState.collectAsState()
    val canControl = connection == ConnectionState.Connected
    // Discover goes when the TV's profile has none; don't leave the tab showing.
    if (tab == Tab.DISCOVER && library?.hasDiscover == false) tab = Tab.HOME

    if (pairing) {
        PairingPage(viewModel, onDone = { pairing = false })
        return
    }

    Scaffold(
        topBar = { ConnectionBanner(connection, onRetry = viewModel::reconnect, onPair = { pairing = true }) },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = nowPlaying != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    nowPlaying?.let { live ->
                        NowPlayingBar(live, onExpand = { nowPlayingExpanded = true }, onCommand = viewModel::send)
                    }
                }
                ShortNavigationBar {
                    Tab.entries.filter { it != Tab.DISCOVER || library?.hasDiscover == true }.forEach { item ->
                        ShortNavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(if (tab == item) item.selectedIcon else item.icon, contentDescription = null) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        // content stops at the status bar instead of scrolling under it (Home draws its
        // spotlight beneath it instead); the bottom padding stays inside lists so they
        // scroll behind the floating Now Playing bar.
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topInset = if (connectionMessage(connection, LocalDeviceNoun.current) == null) statusBar else 0.dp
        val contentPadding = PaddingValues(top = 8.dp, bottom = padding.calculateBottomPadding())
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tab",
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
        ) { current ->
            val lib = library
            val onOpen: (LibraryEntry) -> Unit = { openEntry = it }
            if (current == Tab.HOME && connection != ConnectionState.NotPaired && lib != null) {
                HomeScreen(
                    library = lib,
                    topInset = topInset,
                    bottomPadding = padding.calculateBottomPadding(),
                    canPlay = canControl,
                    onOpen = onOpen,
                    onPlay = { media -> viewModel.playOnTv(media.id) },
                    onResume = { media -> openEntry = lib.entryFor(media) },
                    librarySource = tvState?.librarySource.orEmpty()
                )
                return@AnimatedContent
            }
            Box(Modifier.padding(top = topInset)) { when {
                current == Tab.SETTINGS -> SettingsScreen(viewModel, contentPadding, onPair = { pairing = true })
                connection == ConnectionState.NotPaired -> Box(Modifier.padding(contentPadding)) {
                    NotPairedPrompt(onPair = { pairing = true })
                }
                lib == null -> LibraryLoading(Modifier.padding(contentPadding))
                current == Tab.DISCOVER -> DiscoverScreen(lib, contentPadding, onOpen)
                else -> LibraryScreen(lib, contentPadding, onOpen)
            } }
        }
    }

    // re-resolve against the latest library so progress and favourites stay current.
    val entry = openEntry?.let { e ->
        val lib = library
        when (e) {
            is LibraryEntry.Movie -> lib?.items?.firstOrNull { it.id == e.media.id }?.let(LibraryEntry::Movie) ?: e
            is LibraryEntry.Show -> lib?.show(e.group.title)?.let(LibraryEntry::Show) ?: e
        }
    }
    if (entry != null) {
        DetailsSheet(
            entry = entry,
            canControlTv = canControl,
            isDiscover = (entry as? LibraryEntry.Movie)?.media?.let { library?.isDiscover(it) } == true,
            onDismiss = { openEntry = null },
            onPlay = { media, fromStart ->
                viewModel.playOnTv(media.id, fromStart)
                openEntry = null
            },
            onFavourite = { media, fav -> viewModel.setFavourite(media.id, fav) }
        )
    }
    val live = nowPlaying
    if (nowPlayingExpanded && live != null) {
        NowPlayingSheet(live, onDismiss = { nowPlayingExpanded = false }, onCommand = viewModel::send, loadFrame = viewModel::chapterFrame)
    } else if (live == null) {
        nowPlayingExpanded = false
    }
}

private fun connectionMessage(connection: ConnectionState, noun: String): String? = when (connection) {
    ConnectionState.Connected, ConnectionState.NotPaired -> null
    ConnectionState.Connecting -> "Connecting to your $noun…"
    is ConnectionState.Offline -> "Can't reach your $noun · Is Aperture open on it?"
    ConnectionState.Revoked -> "This phone was removed on the $noun"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectionBanner(connection: ConnectionState, onRetry: () -> Unit, onPair: () -> Unit) {
    val message = connectionMessage(connection, LocalDeviceNoun.current)
    AnimatedVisibility(visible = message != null, enter = expandVertically(), exit = shrinkVertically()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (connection == ConnectionState.Connecting) {
                    LoadingIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(message.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                when (connection) {
                    is ConnectionState.Offline -> TextButton(onClick = onRetry) { Text("Retry") }
                    ConnectionState.Revoked -> TextButton(onClick = onPair) { Text("Pair again") }
                    else -> Unit
                }
            }
        }
    }
}
