package me.xdan.aperture.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import me.xdan.aperture.ui.screen.home.HomeScreen
import me.xdan.aperture.ui.screen.search.SearchScreen
import me.xdan.aperture.ui.screen.player.PlayerScreen
import me.xdan.aperture.ui.screen.home.HomeViewModel
import me.xdan.aperture.ui.screen.details.MediaDetailsModal
import me.xdan.aperture.ui.screen.onboarding.OnboardingScreen
import me.xdan.aperture.ui.screen.settings.SettingsScreen
import me.xdan.aperture.ui.screen.mylist.MyListScreen
import me.xdan.aperture.ui.screen.library.LibraryViewModel
import me.xdan.aperture.ui.screen.library.MoviesScreen
import me.xdan.aperture.ui.screen.library.ShowsScreen
import me.xdan.aperture.ui.component.ProvideFocusMemory
import me.xdan.aperture.ui.component.MediaContextMenu
import me.xdan.aperture.ui.screen.actions.MediaActionsViewModel
import me.xdan.aperture.ui.screen.onboarding.AppTutorial

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NavGraph(
    backstack: NavBackStack<Destination>,
    onNavigate: (Destination) -> Unit,
    mainViewModel: me.xdan.aperture.ui.MainViewModel = viewModel()
) {
    val homeViewModel: HomeViewModel = viewModel()
    val mediaActionsViewModel: MediaActionsViewModel = viewModel()
    val mediaActionState by mediaActionsViewModel.state.collectAsState()
    val currentDestination = backstack.last()
    val showDrawer = currentDestination !is Destination.Player
    val currentFocusKey = currentDestination.focusKey()
    
    var selectedMediaId by remember { mutableStateOf<Long?>(null) }
    var selectedEpisodeOnly by remember { mutableStateOf(false) }
    var contextMediaId by remember { mutableStateOf<Long?>(null) }
    var contextFromContinue by remember { mutableStateOf(false) }
    var contextOpensToRight by remember { mutableStateOf(true) }
    var contextFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    val lastFocusedRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    var homeRestoreFocusKey by remember { mutableStateOf<String?>(null) }
    var settingsRestoreFocusKey by remember { mutableStateOf<String?>(null) }
    var playerOriginFocusKey by remember { mutableStateOf<String?>(null) }
    var pendingPlayerFocusRestore by remember { mutableStateOf<String?>(null) }
    val homeDrawerRequester = remember { FocusRequester() }
    val searchDrawerRequester = remember { FocusRequester() }
    val moviesDrawerRequester = remember { FocusRequester() }
    val showsDrawerRequester = remember { FocusRequester() }
    val myListDrawerRequester = remember { FocusRequester() }
    val settingsDrawerRequester = remember { FocusRequester() }
    val homeContentEntryRequester = remember { FocusRequester() }
    val searchContentEntryRequester = remember { FocusRequester() }
    val settingsContentEntryRequester = remember { FocusRequester() }
    val myListContentEntryRequester = remember { FocusRequester() }
    val moviesContentEntryRequester = remember { FocusRequester() }
    val showsContentEntryRequester = remember { FocusRequester() }
    val drawerRequesters = remember {
        mapOf(
            "home" to homeDrawerRequester,
            "search" to searchDrawerRequester,
            "movies" to moviesDrawerRequester,
            "shows" to showsDrawerRequester,
            "my_list" to myListDrawerRequester,
            "settings" to settingsDrawerRequester
        )
    }
    val contentEntryRequesters = remember {
        mapOf(
            "home" to homeContentEntryRequester,
            "search" to searchContentEntryRequester,
            "movies" to moviesContentEntryRequester,
            "shows" to showsContentEntryRequester,
            "my_list" to myListContentEntryRequester,
            "settings" to settingsContentEntryRequester
        )
    }
    // Every drawer item returns to the content page that opened the drawer.
    // Its label is a destination, but the drawer itself is an overlay on the
    // current destination, so Right must not jump into the hovered label.
    val drawerReturnFocusRequester = currentFocusKey?.let { key ->
        lastFocusedRequesters[key] ?: contentEntryRequesters[key]
    }
    val navigateFromDrawer: (Destination) -> Unit = drawerNavigate@ { destination ->
        if (destination.focusKey() == currentFocusKey) return@drawerNavigate
        if (
            currentDestination is Destination.Home &&
            destination !is Destination.Home
        ) {
            lastFocusedRequesters.remove("home")
            homeRestoreFocusKey = HOME_DEFAULT_FOCUS_KEY
        }
        if (
            currentDestination is Destination.Settings &&
            destination !is Destination.Settings
        ) {
            lastFocusedRequesters.remove("settings")
            settingsRestoreFocusKey = null
        }
        if (destination is Destination.Home) {
            lastFocusedRequesters.remove("home")
            homeRestoreFocusKey = HOME_DEFAULT_FOCUS_KEY
        }
        if (destination is Destination.Movies) {
            // A requester remembered from a previous Movies composition may
            // point to an off-screen or disposed grid card after visiting a
            // different tab. A genuine tab switch re-enters at the first card;
            // simply opening and closing the drawer still preserves position.
            lastFocusedRequesters.remove("movies")
        }
        if (destination is Destination.Search) {
            lastFocusedRequesters.remove("search")
        }
        if (destination is Destination.Shows) {
            lastFocusedRequesters.remove("shows")
        }
        if (destination is Destination.MyList) {
            lastFocusedRequesters.remove("my_list")
        }
        while (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
        if (destination !is Destination.Home) backstack.add(destination)
    }
    val returnFromPlayer: () -> Unit = {
        val originFocusKey = playerOriginFocusKey ?: "home"
        lastFocusedRequesters.remove(originFocusKey)
        if (originFocusKey == "home") {
            homeRestoreFocusKey = HOME_DEFAULT_FOCUS_KEY
        }
        pendingPlayerFocusRestore = originFocusKey
        if (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
    }
    val isOnboardingCompleted by mainViewModel.isOnboardingCompleted.collectAsState()
    val libraryPreparation by mainViewModel.libraryPreparation.collectAsState()
    val tutorialRequired by mainViewModel.isTutorialRequired.collectAsState()
    val tutorialExampleMedia by mainViewModel.tutorialExampleMedia.collectAsState()

    LaunchedEffect(contextMediaId) {
        if (contextMediaId != null) return@LaunchedEffect
        val requester = contextFocusRequester ?: return@LaunchedEffect
        delay(140)
        val restored = runCatching { requester.requestFocus() }.getOrDefault(false)
        if (!restored) {
            val fallback = when (currentFocusKey) {
                "home" -> homeContentEntryRequester
                "search" -> searchContentEntryRequester
                "movies" -> moviesContentEntryRequester
                "shows" -> showsContentEntryRequester
                "my_list" -> myListContentEntryRequester
                "settings" -> settingsContentEntryRequester
                else -> null
            }
            fallback?.let { runCatching { it.requestFocus() } }
        }
        contextFocusRequester = null
    }

    LaunchedEffect(currentDestination, pendingPlayerFocusRestore) {
        val focusKey = pendingPlayerFocusRestore ?: return@LaunchedEffect
        if (currentFocusKey != focusKey || currentDestination is Destination.Player) {
            return@LaunchedEffect
        }

        delay(160)
        val rememberedRequester = lastFocusedRequesters[focusKey]
        val fallbackRequester = when (focusKey) {
            "home" -> homeContentEntryRequester
            "search" -> searchContentEntryRequester
            "my_list" -> myListContentEntryRequester
            "movies" -> moviesContentEntryRequester
            "shows" -> showsContentEntryRequester
            "settings" -> settingsContentEntryRequester
            else -> null
        }
        val restoredRememberedFocus = rememberedRequester?.let { requester ->
            runCatching { requester.requestFocus() }.getOrDefault(false)
        } == true
        if (!restoredRememberedFocus) {
            fallbackRequester?.let { requester ->
                runCatching { requester.requestFocus() }
            }
        }
        pendingPlayerFocusRestore = null
        playerOriginFocusKey = null
    }

    if (isOnboardingCompleted == null) {
        // Loading state, show nothing or a splash screen
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (isOnboardingCompleted == false) {
        OnboardingScreen(
            progress = libraryPreparation,
            onStartPreparation = mainViewModel::startLibraryPreparation,
            onSkip = { mainViewModel.completeOnboarding(showTutorial = false) },
            onComplete = { mainViewModel.completeOnboarding(showTutorial = true) }
        )
    } else {
        ProvideFocusMemory {
            if (showDrawer) {
                NavigationDrawer(
                    drawerContent = { _ ->
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Logo and Title
                            Row(
                                modifier = Modifier.padding(bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = me.xdan.aperture.R.mipmap.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Aperture",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }

                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Home,
                                onClick = {
                                    if (currentDestination is Destination.Home) {
                                        homeRestoreFocusKey = HOME_DEFAULT_FOCUS_KEY
                                        lastFocusedRequesters["home"] = homeContentEntryRequester
                                        homeViewModel.regenerateSuggestions()
                                    } else {
                                        navigateFromDrawer(Destination.Home)
                                    }
                                },
                                modifier = Modifier
                                    .focusRequester(homeDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: homeContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.Home, contentDescription = null) }
                            ) {
                                Text("Home")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Search,
                                onClick = { navigateFromDrawer(Destination.Search) },
                                modifier = Modifier
                                    .focusRequester(searchDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: searchContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.Search, contentDescription = null) }
                            ) {
                                Text("Search")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Movies,
                                onClick = { navigateFromDrawer(Destination.Movies) },
                                modifier = Modifier
                                    .focusRequester(moviesDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: moviesContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.Movie, contentDescription = null) }
                            ) {
                                Text("Movies")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Shows,
                                onClick = { navigateFromDrawer(Destination.Shows) },
                                modifier = Modifier
                                    .focusRequester(showsDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: showsContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.Tv, contentDescription = null) }
                            ) {
                                Text("Shows")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.MyList,
                                onClick = { navigateFromDrawer(Destination.MyList) },
                                modifier = Modifier
                                    .focusRequester(myListDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: myListContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) }
                            ) {
                                Text("My List")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Settings,
                                onClick = {
                                    // Preserve position when simply returning from the drawer.
                                    // A real destination change gets a fresh Settings composition,
                                    // starting at Theme instead.
                                    if (currentDestination !is Destination.Settings) {
                                        lastFocusedRequesters.remove("settings")
                                        settingsRestoreFocusKey = null
                                        navigateFromDrawer(Destination.Settings)
                                    }
                                },
                                modifier = Modifier
                                    .focusRequester(settingsDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: settingsContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = null) }
                            ) {
                                Text("Settings")
                            }
                        }
                    }
                ) {
                    NavContent(
                        homeViewModel = homeViewModel,
                        backstack = backstack,
                        drawerRequesters = drawerRequesters,
                        contentEntryRequesters = contentEntryRequesters,
                        homeRestoreFocusKey = homeRestoreFocusKey,
                        settingsRestoreFocusKey = settingsRestoreFocusKey,
                        // Card-to-card focus is already held by lastFocusedRequesters.
                        // Updating navigation state for every DPAD move causes Home's
                        // parent LazyColumn to recompose and visibly wobble.
                        onHomeFocusKeyChanged = {},
                        onSettingsFocusKeyChanged = { settingsRestoreFocusKey = it },
                        onPlayerBack = returnFromPlayer,
                        onActiveMediaChanged = mainViewModel::setActiveMedia,
                        onContentFocused = { focusKey, requester ->
                            lastFocusedRequesters[focusKey] = requester
                        },
                        onMediaClick = { focusKey, mediaId, requester, episodeOnly ->
                            lastFocusedRequesters[focusKey] = requester
                            mainViewModel.setActiveMedia(mediaId)
                            selectedEpisodeOnly = episodeOnly
                            selectedMediaId = mediaId
                        },
                        onMediaLongClick = { focusKey, media, requester, fromContinue, opensToRight ->
                            lastFocusedRequesters[focusKey] = requester
                            contextMediaId = media.id
                            contextFromContinue = fromContinue
                            contextFocusRequester = requester
                            contextOpensToRight = opensToRight
                            mediaActionsViewModel.load(media.id)
                        }
                    )
                }
            } else {
                NavContent(
                    homeViewModel = homeViewModel,
                    backstack = backstack,
                    drawerRequesters = emptyMap(),
                    contentEntryRequesters = contentEntryRequesters,
                    homeRestoreFocusKey = homeRestoreFocusKey,
                    settingsRestoreFocusKey = settingsRestoreFocusKey,
                    onHomeFocusKeyChanged = {},
                    onSettingsFocusKeyChanged = { settingsRestoreFocusKey = it },
                    onPlayerBack = returnFromPlayer,
                    onActiveMediaChanged = mainViewModel::setActiveMedia,
                    onContentFocused = { focusKey, requester ->
                        lastFocusedRequesters[focusKey] = requester
                    },
                    onMediaClick = { focusKey, mediaId, requester, episodeOnly ->
                        lastFocusedRequesters[focusKey] = requester
                        mainViewModel.setActiveMedia(mediaId)
                        selectedEpisodeOnly = episodeOnly
                        selectedMediaId = mediaId
                    },
                    onMediaLongClick = { _, media, requester, fromContinue, opensToRight ->
                        contextMediaId = media.id
                        contextFromContinue = fromContinue
                        contextFocusRequester = requester
                        contextOpensToRight = opensToRight
                        mediaActionsViewModel.load(media.id)
                    }
                )
            }

            contextMediaId?.takeIf { mediaActionState.media?.id == it }?.let { mediaId ->
                MediaContextMenu(
                    state = mediaActionState,
                    fromContinueWatching = contextFromContinue,
                    opensToRight = contextOpensToRight,
                    onDismiss = {
                        contextMediaId = null
                    },
                    onInfo = {
                        contextFocusRequester = null
                        contextMediaId = null
                        mainViewModel.setActiveMedia(mediaId)
                        selectedEpisodeOnly = contextFromContinue && mediaActionState.media?.type == "EPISODE"
                        selectedMediaId = mediaId
                    },
                    onPlayFromBeginning = {
                        contextFocusRequester = null
                        contextMediaId = null
                        playerOriginFocusKey = currentFocusKey
                        onNavigate(Destination.Player(mediaId, true))
                    },
                    onRemoveContinue = {
                        mediaActionsViewModel.clearProgress(mediaId)
                        contextMediaId = null
                    },
                    onToggleList = {
                        mediaActionsViewModel.toggleFavorite(mediaId)
                        contextMediaId = null
                    },
                    onToggleWatched = {
                        mediaActionsViewModel.toggleWatched(mediaId)
                        contextMediaId = null
                    },
                    onHide = {
                        mediaActionsViewModel.hide(mediaId)
                        contextMediaId = null
                    },
                    onRefreshAssets = {
                        mediaActionsViewModel.refreshAssets(mediaId)
                        contextMediaId = null
                    }
                )
            }

            MediaDetailsModal(
                mediaId = selectedMediaId,
                episodeOnly = selectedEpisodeOnly,
                viewModel = viewModel(),
                onPlay = { mediaId, startFromBeginning ->
                    selectedMediaId = null
                    playerOriginFocusKey = currentFocusKey
                    onNavigate(Destination.Player(mediaId, startFromBeginning))
                },
                onClose = { selectedMediaId = null },
                restoreFocus = {
                    val focusKey = currentFocusKey
                    val rememberedRequester = focusKey?.let { lastFocusedRequesters[it] }
                    val fallbackRequester = when (focusKey) {
                        "home" -> homeContentEntryRequester
                        "search" -> searchContentEntryRequester
                        "my_list" -> myListContentEntryRequester
                        "movies" -> moviesContentEntryRequester
                        "shows" -> showsContentEntryRequester
                        "settings" -> settingsContentEntryRequester
                        else -> null
                    }
                    val restored = rememberedRequester?.let { requester ->
                        runCatching { requester.requestFocus() }.getOrDefault(false)
                    } == true
                    if (!restored) {
                        fallbackRequester?.let { requester ->
                            runCatching { requester.requestFocus() }
                        }
                    }
                }
            )

            if (tutorialRequired) {
                AppTutorial(
                    exampleTitle = tutorialExampleMedia?.title,
                    onNavigate = { destination ->
                        if (currentDestination::class != destination::class) navigateFromDrawer(destination)
                    },
                    onShowExample = { show ->
                        selectedMediaId = tutorialExampleMedia?.id.takeIf { show }
                    },
                    onFinish = mainViewModel::completeTutorial
                )
            }
        }
    }
}

private const val HOME_DEFAULT_FOCUS_KEY = "spotlight"

private fun Destination.focusKey(): String? = when (this) {
    Destination.Home -> "home"
    Destination.Search -> "search"
    Destination.Movies -> "movies"
    Destination.Shows -> "shows"
    Destination.MyList -> "my_list"
    Destination.Settings -> "settings"
    is Destination.Player -> null
}

@Composable
private fun NavContent(
    homeViewModel: HomeViewModel,
    backstack: NavBackStack<Destination>,
    onMediaClick: (String, Long, FocusRequester, Boolean) -> Unit,
    onMediaLongClick: (String, me.xdan.aperture.data.local.entity.MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerRequesters: Map<String, FocusRequester>,
    contentEntryRequesters: Map<String, FocusRequester>,
    homeRestoreFocusKey: String?,
    settingsRestoreFocusKey: String?,
    onHomeFocusKeyChanged: (String) -> Unit,
    onSettingsFocusKeyChanged: (String) -> Unit,
    onPlayerBack: () -> Unit,
    onActiveMediaChanged: (Long) -> Unit,
    onContentFocused: (String, FocusRequester) -> Unit
) {
    NavDisplay(
        backStack = backstack
    ) { destination ->
        NavEntry<Destination>(destination) {
            val focusKey = destination.focusKey()
            val drawerFocusRequester = focusKey?.let(drawerRequesters::get)
            val contentEntryFocusRequester = focusKey?.let(contentEntryRequesters::get)
                ?: FocusRequester.Default
            val mediaClick: (Long, FocusRequester) -> Unit = { mediaId, requester ->
                focusKey?.let { onMediaClick(it, mediaId, requester, false) }
            }
            val episodeAwareMediaClick: (Long, FocusRequester, Boolean) -> Unit =
                { mediaId, requester, episodeOnly ->
                    focusKey?.let { onMediaClick(it, mediaId, requester, episodeOnly) }
                }
            val mediaLongClick: (me.xdan.aperture.data.local.entity.MediaEntity, FocusRequester, Boolean, Boolean) -> Unit =
                { media, requester, fromContinue, opensToRight ->
                    focusKey?.let { onMediaLongClick(it, media, requester, fromContinue, opensToRight) }
                }
            val contentFocused: (FocusRequester) -> Unit = { requester ->
                focusKey?.let { onContentFocused(it, requester) }
            }
            when (destination) {
                is Destination.Home -> HomeScreen(
                    viewModel = homeViewModel,
                    onMediaClick = episodeAwareMediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    restoreFocusKey = homeRestoreFocusKey,
                    onFocusKeyChanged = onHomeFocusKeyChanged,
                    onContentFocused = contentFocused,
                    onActiveMediaChanged = onActiveMediaChanged
                )
                is Destination.Search -> SearchScreen(
                    viewModel = viewModel(),
                    onMediaClick = mediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused
                )
                is Destination.MyList -> MyListScreen(
                    viewModel = viewModel(),
                    onMediaClick = mediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused
                )
                is Destination.Movies -> MoviesScreen(
                    viewModel = viewModel(),
                    onMediaClick = mediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused,
                    onActiveMediaChanged = onActiveMediaChanged
                )
                is Destination.Shows -> ShowsScreen(
                    viewModel = viewModel(),
                    onMediaClick = episodeAwareMediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused,
                    onActiveMediaChanged = onActiveMediaChanged
                )
                is Destination.Settings -> SettingsScreen(
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    restoreFocusKey = settingsRestoreFocusKey,
                    onFocusKeyChanged = onSettingsFocusKeyChanged,
                    onContentFocused = contentFocused
                )
                is Destination.Player -> PlayerScreen(
                    mediaId = destination.mediaId,
                    startFromBeginning = destination.startFromBeginning,
                    viewModel = viewModel(),
                    onBack = onPlayerBack,
                    onFinished = onPlayerBack
                )
                else -> Box(modifier = Modifier.fillMaxSize()) {
                    Text("Coming Soon", modifier = Modifier.padding(32.dp))
                }
            }
        }
    }
}
