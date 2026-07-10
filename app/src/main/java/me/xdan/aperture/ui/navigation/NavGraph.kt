package me.xdan.aperture.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var lastFocusedMediaId by remember { mutableStateOf<Long?>(null) }
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
                                onClick = { onNavigate(Destination.Home) },
                                leadingContent = { Icon(Icons.Rounded.Home, contentDescription = null) }
                            ) {
                                Text("Home")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Search,
                                onClick = { onNavigate(Destination.Search) },
                                leadingContent = { Icon(Icons.Rounded.Search, contentDescription = null) }
                            ) {
                                Text("Search")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Movies,
                                onClick = { onNavigate(Destination.Movies) },
                                leadingContent = { Icon(Icons.Rounded.Movie, contentDescription = null) }
                            ) {
                                Text("Movies")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Shows,
                                onClick = { onNavigate(Destination.Shows) },
                                leadingContent = { Icon(Icons.Rounded.Tv, contentDescription = null) }
                            ) {
                                Text("Shows")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.MyList,
                                onClick = { onNavigate(Destination.MyList) },
                                leadingContent = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) }
                            ) {
                                Text("My List")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Settings,
                                onClick = { onNavigate(Destination.Settings) },
                                leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = null) }
                            ) {
                                Text("Settings")
                            }
                        }
                    }
                ) {
                    NavContent(backstack, onNavigate, onMediaClick = { mediaId ->
                        lastFocusedMediaId = mediaId
                        selectedMediaId = mediaId
                    })
                }
            } else {
                NavContent(backstack, onNavigate, onMediaClick = { mediaId ->
                    lastFocusedMediaId = mediaId
                    selectedMediaId = mediaId
                })
            }

            selectedMediaId?.let { mediaId ->
                MediaDetailsModal(
                    mediaId = mediaId,
                    viewModel = viewModel(),
                    onPlay = { 
                        selectedMediaId = null
                        onNavigate(Destination.Player(it))
                    },
                    onClose = { selectedMediaId = null },
                    restoreFocus = {
                        // Focus will be restored by the MediaCard component when it regains focus
                        lastFocusedMediaId = null
                    }
                )
            }
        }
    }
}

@Composable
private fun NavContent(
    backstack: NavBackStack<Destination>,
    onNavigate: (Destination) -> Unit,
    onMediaClick: (Long) -> Unit
) {
    NavDisplay(
        backStack = backstack
    ) { destination ->
        NavEntry<Destination>(destination) {
            when (destination) {
                is Destination.Home -> HomeScreen(
                    viewModel = viewModel(),
                    onMediaClick = onMediaClick
                )
                is Destination.Search -> SearchScreen(
                    viewModel = viewModel(),
                    onMediaClick = onMediaClick
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
