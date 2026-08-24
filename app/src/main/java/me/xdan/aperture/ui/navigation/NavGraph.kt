package me.xdan.aperture.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.xdan.aperture.data.update.UpdateCheckState
import me.xdan.aperture.ui.component.MediaContextMenu
import me.xdan.aperture.ui.component.ProvideFocusMemory
import me.xdan.aperture.ui.screen.actions.MediaActionsViewModel
import me.xdan.aperture.ui.screen.details.MediaDetailsModal
import me.xdan.aperture.ui.screen.home.HomeScreen
import me.xdan.aperture.ui.screen.home.HomeViewModel
import me.xdan.aperture.ui.screen.library.MoviesScreen
import me.xdan.aperture.ui.screen.library.ShowsScreen
import me.xdan.aperture.ui.screen.mylist.MyListScreen
import me.xdan.aperture.ui.screen.onboarding.AppTutorial
import me.xdan.aperture.ui.screen.onboarding.OnboardingScreen
import me.xdan.aperture.ui.screen.player.PlayerScreen
import me.xdan.aperture.ui.screen.search.SearchScreen
import me.xdan.aperture.ui.screen.settings.SettingsScreen
import me.xdan.aperture.ui.component.expressive.ExpressiveLoadingIndicator

@Composable
fun NavGraph(
    backstack: NavBackStack<Destination>,
    onNavigate: (Destination) -> Unit,
    mainViewModel: me.xdan.aperture.ui.MainViewModel = viewModel(),
    onPlayerStateChanged: (Boolean) -> Unit = {},
    onPreviewAmbientMode: () -> Unit = {}
) {
    val homeViewModel: HomeViewModel = viewModel()
    val mediaActionsViewModel: MediaActionsViewModel = viewModel()
    val mediaActionState by mediaActionsViewModel.state.collectAsState()
    val currentDestination = backstack.last()
    val showNavigation = currentDestination !is Destination.Player
    val currentFocusKey = currentDestination.focusKey()

    LaunchedEffect(currentDestination is Destination.Player) {
        onPlayerStateChanged(currentDestination is Destination.Player)
    }

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
    var isRescanVisible by remember { mutableStateOf(false) }
    var topNavigationHasFocus by remember { mutableStateOf(false) }

    val homeTopNavRequester = remember { FocusRequester() }
    val searchTopNavRequester = remember { FocusRequester() }
    val moviesTopNavRequester = remember { FocusRequester() }
    val showsTopNavRequester = remember { FocusRequester() }
    val myListTopNavRequester = remember { FocusRequester() }
    val settingsTopNavRequester = remember { FocusRequester() }
    val homeContentEntryRequester = remember { FocusRequester() }
    val searchContentEntryRequester = remember { FocusRequester() }
    val settingsContentEntryRequester = remember { FocusRequester() }
    val myListContentEntryRequester = remember { FocusRequester() }
    val moviesContentEntryRequester = remember { FocusRequester() }
    val showsContentEntryRequester = remember { FocusRequester() }

    val topNavigationRequesters = remember {
        mapOf(
            "home" to homeTopNavRequester,
            "search" to searchTopNavRequester,
            "movies" to moviesTopNavRequester,
            "shows" to showsTopNavRequester,
            "my_list" to myListTopNavRequester,
            "settings" to settingsTopNavRequester,
        )
    }
    val contentEntryRequesters = remember {
        mapOf(
            "home" to homeContentEntryRequester,
            "search" to searchContentEntryRequester,
            "movies" to moviesContentEntryRequester,
            "shows" to showsContentEntryRequester,
            "my_list" to myListContentEntryRequester,
            "settings" to settingsContentEntryRequester,
        )
    }

    val focusScope = rememberCoroutineScope()
    val pendingFocusJob = remember { arrayOfNulls<Job>(1) }

    fun requestFocusWhenReady(requester: FocusRequester?) {
        pendingFocusJob[0]?.cancel()
        if (requester == null) return
        pendingFocusJob[0] = focusScope.launch {
            delay(120)
            repeat(10) {
                if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@launch
                delay(80)
            }
        }
    }

    val currentTopNavigationRequester = currentFocusKey?.let(topNavigationRequesters::get)
    val contentReturnFocusRequester = currentFocusKey?.let { key ->
        lastFocusedRequesters[key] ?: contentEntryRequesters[key]
    }

    fun navigateFromTopNavigation(destination: Destination) {
        if (destination.focusKey() == currentFocusKey) return

        if (currentDestination is Destination.Home && destination !is Destination.Home) {
            lastFocusedRequesters.remove("home")
            homeRestoreFocusKey = HOME_DEFAULT_FOCUS_KEY
        }
        if (currentDestination is Destination.Settings && destination !is Destination.Settings) {
            lastFocusedRequesters.remove("settings")
            settingsRestoreFocusKey = null
        }
        if (destination is Destination.Home) {
            lastFocusedRequesters.remove("home")
            homeRestoreFocusKey = HOME_DEFAULT_FOCUS_KEY
        }
        if (destination is Destination.Movies) lastFocusedRequesters.remove("movies")
        if (destination is Destination.Search) lastFocusedRequesters.remove("search")
        if (destination is Destination.Shows) lastFocusedRequesters.remove("shows")
        if (destination is Destination.MyList) lastFocusedRequesters.remove("my_list")
        if (destination is Destination.Settings) {
            lastFocusedRequesters.remove("settings")
            settingsRestoreFocusKey = null
        }

        while (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
        if (destination !is Destination.Home) backstack.add(destination)

        if (destination is Destination.Home) homeViewModel.regenerateSuggestions()
    }

    val returnFromPlayer: () -> Unit = {
        val originFocusKey = playerOriginFocusKey ?: "home"
        lastFocusedRequesters.remove(originFocusKey)
        if (originFocusKey == "home") homeRestoreFocusKey = HOME_DEFAULT_FOCUS_KEY
        pendingPlayerFocusRestore = originFocusKey
        if (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
    }

    val isOnboardingCompleted by mainViewModel.isOnboardingCompleted.collectAsState()
    val libraryPreparation by mainViewModel.libraryPreparation.collectAsState()
    val tutorialRequired by mainViewModel.isTutorialRequired.collectAsState()
    val tutorialExampleMedia by mainViewModel.tutorialExampleMedia.collectAsState()

    LaunchedEffect(isOnboardingCompleted, currentDestination, pendingPlayerFocusRestore) {
        if (
            isOnboardingCompleted == true &&
            showNavigation &&
            pendingPlayerFocusRestore == null &&
            !tutorialRequired &&
            selectedMediaId == null &&
            contextMediaId == null
        ) {
            requestFocusWhenReady(currentTopNavigationRequester)
        }
    }

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
        if (currentFocusKey != focusKey || currentDestination is Destination.Player) return@LaunchedEffect

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
        val restoredRememberedFocus = rememberedRequester?.let {
            runCatching { it.requestFocus() }.getOrDefault(false)
        } == true
        if (!restoredRememberedFocus) fallbackRequester?.let { runCatching { it.requestFocus() } }
        topNavigationHasFocus = false
        pendingPlayerFocusRestore = null
        playerOriginFocusKey = null
    }

    if (isRescanVisible) {
        val finishRescan: () -> Unit = {
            isRescanVisible = false
            while (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
        }
        BackHandler(onBack = finishRescan)
        OnboardingScreen(
            progress = libraryPreparation,
            onStartPreparation = { mainViewModel.startLibraryPreparation(force = true) },
            onSkip = finishRescan,
            onComplete = finishRescan,
            rescanMode = true,
            onRescanComplete = finishRescan,
        )
    } else if (isOnboardingCompleted == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ExpressiveLoadingIndicator()
        }
    } else if (isOnboardingCompleted == false) {
        OnboardingScreen(
            progress = libraryPreparation,
            onStartPreparation = mainViewModel::startLibraryPreparation,
            onSkip = { mainViewModel.completeOnboarding(showTutorial = false) },
            onComplete = { mainViewModel.completeOnboarding(showTutorial = true) },
        )
    } else {
        ProvideFocusMemory {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showNavigation) {
                    TopNavigationBar(
                        selectedDestination = currentDestination,
                        requesters = topNavigationRequesters,
                        onDestinationFocused = { destination ->
                            topNavigationHasFocus = true
                            navigateFromTopNavigation(destination)
                        },
                    )
                }

                NavContent(
                    homeViewModel = homeViewModel,
                    backstack = backstack,
                    drawerRequesters = topNavigationRequesters,
                    contentEntryRequesters = contentEntryRequesters,
                    homeRestoreFocusKey = homeRestoreFocusKey,
                    settingsRestoreFocusKey = settingsRestoreFocusKey,
                    onHomeFocusKeyChanged = { homeRestoreFocusKey = it },
                    onSettingsFocusKeyChanged = { settingsRestoreFocusKey = it },
                    onPlayerBack = returnFromPlayer,
                    onActiveMediaChanged = mainViewModel::setActiveMedia,
                    onForceRescan = {
                        isRescanVisible = true
                        mainViewModel.startLibraryPreparation(force = true)
                    },
                    onPreviewAmbientMode = onPreviewAmbientMode,
                    onContentFocused = { focusKey, requester ->
                        topNavigationHasFocus = false
                        lastFocusedRequesters[focusKey] = requester
                    },
                    onMediaClick = { focusKey, mediaId, requester, episodeOnly ->
                        topNavigationHasFocus = false
                        lastFocusedRequesters[focusKey] = requester
                        mainViewModel.setActiveMedia(mediaId)
                        selectedEpisodeOnly = episodeOnly
                        selectedMediaId = mediaId
                    },
                    onMediaLongClick = { focusKey, media, requester, fromContinue, opensToRight ->
                        topNavigationHasFocus = false
                        lastFocusedRequesters[focusKey] = requester
                        contextMediaId = media.id
                        contextFromContinue = fromContinue
                        contextFocusRequester = requester
                        contextOpensToRight = opensToRight
                        mediaActionsViewModel.load(media.id)
                    },
                )
            }

            if (showNavigation && !topNavigationHasFocus && contextMediaId == null && selectedMediaId == null && !tutorialRequired) {
                BackHandler {
                    requestFocusWhenReady(currentTopNavigationRequester)
                }
            }

            contextMediaId?.takeIf { mediaActionState.media?.id == it }?.let { mediaId ->
                MediaContextMenu(
                    state = mediaActionState,
                    fromContinueWatching = contextFromContinue,
                    opensToRight = contextOpensToRight,
                    onDismiss = { contextMediaId = null },
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
                    },
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
                    val restored = rememberedRequester?.let {
                        runCatching { it.requestFocus() }.getOrDefault(false)
                    } == true
                    if (!restored) fallbackRequester?.let { runCatching { it.requestFocus() } }
                },
            )

            if (tutorialRequired) {
                AppTutorial(
                    exampleTitle = tutorialExampleMedia?.title,
                    onNavigate = { destination ->
                        if (currentDestination::class != destination::class) {
                            navigateFromTopNavigation(destination)
                        }
                    },
                    onShowExample = { show ->
                        selectedMediaId = tutorialExampleMedia?.id.takeIf { show }
                    },
                    onFinish = mainViewModel::completeTutorial,
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
    onForceRescan: () -> Unit,
    onPreviewAmbientMode: () -> Unit,
    onContentFocused: (String, FocusRequester) -> Unit,
) {
    NavDisplay(backStack = backstack) { destination ->
        NavEntry<Destination>(destination) {
            val focusKey = destination.focusKey()
            val drawerFocusRequester = focusKey?.let(drawerRequesters::get)
                ?: FocusRequester.Default
            val contentEntryFocusRequester = focusKey?.let(contentEntryRequesters::get)
                ?: FocusRequester.Default
            val mediaClick: (Long, FocusRequester) -> Unit = { mediaId, requester ->
                focusKey?.let { onMediaClick(it, mediaId, requester, false) }
            }
            val episodeAwareMediaClick: (Long, FocusRequester, Boolean) -> Unit = { mediaId, requester, episodeOnly ->
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
                    onActiveMediaChanged = onActiveMediaChanged,
                )
                is Destination.Search -> SearchScreen(
                    viewModel = viewModel(),
                    onMediaClick = mediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused,
                )
                is Destination.MyList -> MyListScreen(
                    viewModel = viewModel(),
                    onMediaClick = mediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused,
                )
                is Destination.Movies -> MoviesScreen(
                    viewModel = viewModel(),
                    onMediaClick = mediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused,
                    onActiveMediaChanged = onActiveMediaChanged,
                )
                is Destination.Shows -> ShowsScreen(
                    viewModel = viewModel(),
                    onMediaClick = episodeAwareMediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused,
                    onActiveMediaChanged = onActiveMediaChanged,
                )
                is Destination.Settings -> SettingsScreen(
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    restoreFocusKey = settingsRestoreFocusKey,
                    onFocusKeyChanged = onSettingsFocusKeyChanged,
                    onContentFocused = contentFocused,
                    onForceRescan = onForceRescan,
                    onPreviewAmbientMode = onPreviewAmbientMode,
                )
                is Destination.Player -> PlayerScreen(
                    mediaId = destination.mediaId,
                    startFromBeginning = destination.startFromBeginning,
                    viewModel = viewModel(),
                    onBack = onPlayerBack,
                    onFinished = onPlayerBack,
                    onLeavePlayerToOpenSubtitles = {
                        onSettingsFocusKeyChanged("open_subtitles")
                        if (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
                        backstack.add(Destination.Settings)
                    },
                )
                else -> Box(modifier = Modifier.fillMaxSize()) {
                    androidx.tv.material3.Text("Coming Soon", modifier = Modifier.padding(32.dp))
                }
            }
        }
    }
}
