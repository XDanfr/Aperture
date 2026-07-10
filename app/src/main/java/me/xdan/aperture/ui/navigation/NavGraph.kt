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

    var selectedMediaId by remember { mutableStateOf<Long?>(null) }
    var lastFocusedMediaRequester by remember { mutableStateOf<FocusRequester?>(null) }
    val homeDrawerRequester = remember { FocusRequester() }
    val searchDrawerRequester = remember { FocusRequester() }
    val moviesDrawerRequester = remember { FocusRequester() }
    val showsDrawerRequester = remember { FocusRequester() }
    val myListDrawerRequester = remember { FocusRequester() }
    val settingsDrawerRequester = remember { FocusRequester() }
    val homeContentEntryRequester = remember { FocusRequester() }
    val searchContentEntryRequester = remember { FocusRequester() }
    val currentDrawerRequester = when (currentDestination) {
        is Destination.Home -> homeDrawerRequester
        is Destination.Search -> searchDrawerRequester
        is Destination.Movies -> moviesDrawerRequester
        is Destination.Shows -> showsDrawerRequester
        is Destination.MyList -> myListDrawerRequester
        is Destination.Settings -> settingsDrawerRequester
        else -> null
    }
    val isOnboardingCompleted by mainViewModel.isOnboardingCompleted.collectAsState()

    if (isOnboardingCompleted == null) {
        // Loading state, show nothing or a splash screen
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (isOnboardingCompleted == false) {
        OnboardingScreen(onComplete = { mainViewModel.completeOnboarding() })
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
                                    lastFocusedMediaRequester = null
                                    onNavigate(Destination.Home)
                                },
                                modifier = Modifier
                                    .focusRequester(homeDrawerRequester)
                                    .focusProperties {
                                        right = lastFocusedMediaRequester ?: homeContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.Home, contentDescription = null) }
                            ) {
                                Text("Home")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Search,
                                onClick = {
                                    lastFocusedMediaRequester = null
                                    onNavigate(Destination.Search)
                                },
                                modifier = Modifier
                                    .focusRequester(searchDrawerRequester)
                                    .focusProperties {
                                        right = lastFocusedMediaRequester ?: searchContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.Search, contentDescription = null) }
                            ) {
                                Text("Search")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Movies,
                                onClick = {
                                    lastFocusedMediaRequester = null
                                    onNavigate(Destination.Movies)
                                },
                                modifier = Modifier
                                    .focusRequester(moviesDrawerRequester)
                                    .focusProperties { right = lastFocusedMediaRequester ?: FocusRequester.Default },
                                leadingContent = { Icon(Icons.Rounded.Movie, contentDescription = null) }
                            ) {
                                Text("Movies")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Shows,
                                onClick = {
                                    lastFocusedMediaRequester = null
                                    onNavigate(Destination.Shows)
                                },
                                modifier = Modifier
                                    .focusRequester(showsDrawerRequester)
                                    .focusProperties { right = lastFocusedMediaRequester ?: FocusRequester.Default },
                                leadingContent = { Icon(Icons.Rounded.Tv, contentDescription = null) }
                            ) {
                                Text("Shows")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.MyList,
                                onClick = {
                                    lastFocusedMediaRequester = null
                                    onNavigate(Destination.MyList)
                                },
                                modifier = Modifier
                                    .focusRequester(myListDrawerRequester)
                                    .focusProperties { right = lastFocusedMediaRequester ?: FocusRequester.Default },
                                leadingContent = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) }
                            ) {
                                Text("My List")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Settings,
                                onClick = {
                                    lastFocusedMediaRequester = null
                                    onNavigate(Destination.Settings)
                                },
                                modifier = Modifier
                                    .focusRequester(settingsDrawerRequester)
                                    .focusProperties { right = lastFocusedMediaRequester ?: FocusRequester.Default },
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
                            else -> FocusRequester.Default
                        },
                        onContentFocused = { lastFocusedMediaRequester = it },
                        onMediaClick = { mediaId, requester ->
                            lastFocusedMediaRequester = requester
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
                    onContentFocused = { lastFocusedMediaRequester = it },
                    onMediaClick = { mediaId, requester ->
                        lastFocusedMediaRequester = requester
                        selectedMediaId = mediaId
                    }
                )
            }

            MediaDetailsModal(
                mediaId = selectedMediaId,
                viewModel = viewModel(),
                onPlay = {
                    selectedMediaId = null
                    onNavigate(Destination.Player(it))
                },
                onClose = { selectedMediaId = null },
                restoreFocus = {
                    lastFocusedMediaRequester?.let { requester ->
                        runCatching { requester.requestFocus() }
                    }
                }
            )
        }
    }
}

@Composable
private fun NavContent(
    backstack: NavBackStack<Destination>,
    onNavigate: (Destination) -> Unit,
    onMediaClick: (Long, FocusRequester) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
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
                    onContentFocused = onContentFocused
                )
                is Destination.Search -> SearchScreen(
                    viewModel = viewModel(),
                    onMediaClick = onMediaClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = onContentFocused
                )
                is Destination.MyList -> Box(modifier = Modifier.fillMaxSize()) {
                    Text("My List - Coming Soon", modifier = Modifier.padding(32.dp))
                }
                is Destination.Settings -> SettingsScreen()
                is Destination.Player -> PlayerScreen(
                    mediaId = destination.mediaId,
                    viewModel = viewModel(),
                    onBack = { backstack.removeAt(backstack.lastIndex) }
                )
                else -> Box(modifier = Modifier.fillMaxSize()) {
                    Text("Coming Soon", modifier = Modifier.padding(32.dp))
                }
            }
        }
    }
}