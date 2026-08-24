package me.xdan.aperture.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import me.xdan.aperture.ui.component.ApertureBrandMark

@Composable
fun TopNavigationBar(
    selectedDestination: Destination,
    onDestinationFocused: (Destination) -> Unit,
    requesters: Map<String, FocusRequester>,
    modifier: Modifier = Modifier,
) {
    val destinations = listOf(
        Destination.Shows to "Shows",
        Destination.Movies to "Movies",
        Destination.Search to "Search",
        Destination.Home to "Aperture",
        Destination.MyList to "My List",
        Destination.Settings to "Settings",
    )
    var focusedDestination by remember(selectedDestination) { mutableStateOf(selectedDestination) }
    var hasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDestination) {
        focusedDestination = selectedDestination
        if (selectedDestination is Destination.Home) {
            delay(180)
            requesters["home"]?.let { runCatching { it.requestFocus() } }
        }
    }

    BackHandler(enabled = hasFocus) {
        // Stay on the navigation bar rather than allowing the activity to close.
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.dp)
            .zIndex(10f),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .wrapContentSize()
                .width(612.dp)
                .height(64.dp)
                .padding(top = 8.dp),
            shape = RoundedCornerShape(32.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEachIndexed { index, (destination, label) ->
                    val key = destination.focusKey() ?: return@forEachIndexed
                    val selected = focusedDestination.focusKey() == destination.focusKey()
                    val itemWidth by animateDpAsState(
                        targetValue = if (selected) 148.dp else 64.dp,
                        animationSpec = tween(220),
                        label = "topNavItemWidth",
                    )
                    val requester = requesters[key]

                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .height(52.dp)
                            .then(
                                if (requester != null) Modifier.focusRequester(requester) else Modifier
                            )
                            .focusProperties {
                                left = requesters[destinations.getOrNull(index - 1)?.first?.focusKey()]
                                    ?: FocusRequester.Cancel
                                right = requesters[destinations.getOrNull(index + 1)?.first?.focusKey()]
                                    ?: FocusRequester.Cancel
                            }
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    hasFocus = true
                                    focusedDestination = destination
                                    onDestinationFocused(destination)
                                }
                            }
                            .focusable()
                            .clickable { onDestinationFocused(destination) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(26.dp),
                            colors = if (selected) {
                                SurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else {
                                SurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                when (destination) {
                                    Destination.Home -> ApertureBrandMark(
                                        modifier = Modifier.size(24.dp),
                                        spinBlades = selected,
                                    )
                                    Destination.Shows -> Icon(Icons.Rounded.Tv, null, Modifier.size(24.dp))
                                    Destination.Movies -> Icon(Icons.Rounded.Movie, null, Modifier.size(24.dp))
                                    Destination.Search -> Icon(Icons.Rounded.Search, null, Modifier.size(24.dp))
                                    Destination.MyList -> Icon(Icons.Rounded.FavoriteBorder, null, Modifier.size(24.dp))
                                    Destination.Settings -> Icon(Icons.Rounded.Settings, null, Modifier.size(24.dp))
                                    is Destination.Player -> Spacer(Modifier.size(24.dp))
                                }
                                if (selected) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(modifier = Modifier.animateContentSize()) {
                                        Text(label)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
