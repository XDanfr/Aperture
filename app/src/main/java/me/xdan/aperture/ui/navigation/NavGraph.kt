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
import androidx.compose.ui.graphics.vector.ImageVector
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
import me.xdan.aperture.ui.screen.search.SearchViewModel
import me.xdan.aperture.ui.screen.player.PlayerViewModel
import me.xdan.aperture.ui.screen.details.MediaDetailsModal
import me.xdan.aperture.ui.screen.details.MediaDetailsViewModel
import me.xdan.aperture.ui.screen.onboarding.OnboardingScreen
import me.xdan.aperture.ui.screen.settings.SettingsScreen
import me.xdan.aperture.ui.screen.mylist.MyListScreen
import me.xdan.aperture.ui.component.ProvideFocusMemory

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NavGraph(
    backstack: NavBackStack<Destination>,
    onNavigate: (Destination) -> Unit,
    mainViewModel: me.xdan.aperture.ui.MainViewModel = viewModel()
) {
    val currentDestination = backstack.last()
    val showDrawer = currentDestination !is Destination.Player
    val currentFocusKey = when (currentDestination) {
        is Destination.Home -> "home"
        is Destination.Search -> "search"
        is Destination.Movies -> "movies"
        is Destination.Shows -> "shows"
        is Destination.MyList -> "my_list"
        is Destination.Settings -> "settings"
        else -> null
    }
    
    var selectedMediaId by remember { mutableStateOf<Long?>(null) }
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
    val currentDrawerRequester = when (currentDestination) {
        is Destination.Home -> homeDrawerRequester
        is Destination.Search -> searchDrawerRequester
        is Destination.Movies -> moviesDrawerRequester
        is Destination.Shows -> showsDrawerRequester
        is Destination.MyList -> myListDrawerRequester
        is Destination.Settings -> settingsDrawerRequester
        else -> null
    }
    val navigateFromDrawer: (Destination) -> Unit = { destination ->
        if (
            currentDestination is Destination.Home &&
            destination !is Destination.Home
        ) {
            lastFocusedRequesters.remove("home")
        }
        if (
            currentDestination is Destination.Settings &&
            destination !is Destination.Settings
        ) {
            lastFocusedRequesters.remove("settings")
            settingsRestoreFocusKey = null
        }
        onNavigate(destination)
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
            onSkip = mainViewModel::completeOnboarding,
            onComplete = mainViewModel::completeOnboarding
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
                                onClick = { navigateFromDrawer(Destination.Home) },
                                modifier = Modifier
                                    .focusRequester(homeDrawerRequester)
                                    .focusProperties {
                                        right = lastFocusedRequesters["home"]
                                            ?: homeContentEntryRequester
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
                                        right = lastFocusedRequesters["search"] ?: searchContentEntryRequester
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
                                        right = lastFocusedRequesters["movies"] ?: FocusRequester.Default
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
                                        right = lastFocusedRequesters["shows"] ?: FocusRequester.Default
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
                                        right = lastFocusedRequesters["my_list"]
                                            ?: myListContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) }
                            ) {
                                Text("My List")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Settings,
                                onClick = { navigateFromDrawer(Destination.Settings) },
                                modifier = Modifier
                                    .focusRequester(settingsDrawerRequester)
                                    .focusProperties {
                                        right = lastFocusedRequesters["settings"]
                                            ?: settingsContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = null) }
                            ) {
                                Text("Settings")
                            }
                        }
                    }
                ) {
                    NavContent(
                        backstack = backstack,
                        onNavigate = onNavigate,
                        drawerFocusRequester = currentDrawerRequester,
                        contentEntryFocusRequester = when (currentDestination) {
                            is Destination.Home -> homeContentEntryRequester
                            is Destination.Search -> searchContentEntryRequester
                            is Destination.MyList -> myListContentEntryRequester
                            is Destination.Settings -> settingsContentEntryRequester
                            else -> FocusRequester.Default
                        },
                        homeRestoreFocusKey = homeRestoreFocusKey,
                        settingsRestoreFocusKey = settingsRestoreFocusKey,
                        onHomeFocusKeyChanged = { homeRestoreFocusKey = it },
                        onSettingsFocusKeyChanged = { settingsRestoreFocusKey = it },
                        onPlayerBack = returnFromPlayer,
                        onContentFocused = { requester ->
                            currentFocusKey?.let { lastFocusedRequesters[it] = requester }
                        },
                        onMediaClick = { mediaId, requester ->
                            currentFocusKey?.let { lastFocusedRequesters[it] = requester }
                            selectedMediaId = mediaId
                        }
                    )
                }
            } else {
                NavContent(
                    backstack = backstack,
                    onNavigate = onNavigate,
                    drawerFocusRequester = null,
                    contentEntryFocusRequester = FocusRequester.Default,
                    homeRestoreFocusKey = homeRestoreFocusKey,
                    settingsRestoreFocusKey = settingsRestoreFocusKey,
                    onHomeFocusKeyChanged = { homeRestoreFocusKey = it },
                    onSettingsFocusKeyChanged = { settingsRestoreFocusKey = it },
                    onPlayerBack = returnFromPlayer,
                    onContentFocused = { requester ->
                        currentFocusKey?.let { lastFocusedRequesters[it] = requester }
                    },
                    onMediaClick = { mediaId, requester ->
                        currentFocusKey?.let { lastFocusedRequesters[it] = requester }
                        selectedMediaId = mediaId
                    }
                )
            }

            MediaDetailsModal(
                mediaId = selectedMediaId,
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
        }
    }
}

private const val HOME_DEFAULT_FOCUS_KEY = "spotlight"

@Composable
private fun NavContent(
    backstack: NavBackStack<Destination>,
    onNavigate: (Destination) -> Unit,
    onMediaClick: (Long, FocusRequester) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    homeRestoreFocusKey: String?,
    settingsRestoreFocusKey: String?,
    onHomeFocusKeyChanged: (String) -> Unit,
    onSettingsFocusKeyChanged: (String) -> Unit,
    onPlayerBack: () -> Unit,
    onContentFocused: (FocusRequester) -> Unit
) {
    NavDisplay(
        backStack = backstack
    ) { destination ->
        NavEntry<Destination>(destination) {
            when (destination) {
                is Destination.Home -> HomeScreen(
                    viewModel = viewModel(),
                    onMediaClick = onMediaClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    restoreFocusKey = homeRestoreFocusKey,
                    onFocusKeyChanged = onHomeFocusKeyChanged,
                    onContentFocused = onContentFocused
                )
                is Destination.Search -> SearchScreen(
                    viewModel = viewModel(),
                    onMediaClick = onMediaClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = onContentFocused
                )
                is Destination.MyList -> MyListScreen(
                    viewModel = viewModel(),
                    onMediaClick = onMediaClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = onContentFocused
                )
                is Destination.Settings -> SettingsScreen(
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    restoreFocusKey = settingsRestoreFocusKey,
                    onFocusKeyChanged = onSettingsFocusKeyChanged,
                    onContentFocused = onContentFocused
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
