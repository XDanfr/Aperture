package me.xdan.aperture.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import me.xdan.aperture.ui.component.ApertureBrandMark
import me.xdan.aperture.ui.theme.ApertureBrandFontFamily
import me.xdan.aperture.ui.theme.ApertureTheme

private val TopNavigationOuterShape = RoundedCornerShape(32.dp)
private val TopNavigationItemShape = RoundedCornerShape(26.dp)
private val TopNavigationOuterHeight = 64.dp
private val TopNavigationItemHeight = 52.dp
private val TopNavigationSlotWidth = 108.dp
private val TopNavigationSelectedWidth = 140.dp

private data class NavigationItem(
    val destination: Destination,
    val label: String,
)

@Composable
fun TopNavigationBar(
    currentDestination: Destination,
    onDestinationClick: (Destination) -> Unit,
) {
    val items = remember {
        listOf(
            NavigationItem(Destination.Shows, "Shows"),
            NavigationItem(Destination.Movies, "Movies"),
            NavigationItem(Destination.Search, "Search"),
            NavigationItem(Destination.Home, "Aperture"),
            NavigationItem(Destination.MyList, "My List"),
            NavigationItem(Destination.Settings, "Settings"),
        )
    }
    val requesters = remember { List(items.size) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(items.indexOfFirst { it.destination.focusKey() == currentDestination.focusKey() }.coerceAtLeast(0)) }

    LaunchedEffect(currentDestination) {
        val index = items.indexOfFirst { it.destination.focusKey() == currentDestination.focusKey() }
        if (index >= 0) {
            focusedIndex = index
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .animateContentSize(animationSpec = tween(220)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .width(692.dp)
                .sizeIn(maxWidth = 692.dp),
            shape = TopNavigationOuterShape,
            colors = SurfaceDefaults.colors(
                containerColor = ApertureTheme.colorScheme.surface.copy(alpha = 0.70f),
                contentColor = ApertureTheme.colorScheme.onSurface,
            ),
        ) {
            Row(
                modifier = Modifier
                    .height(TopNavigationOuterHeight)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val isFocused = focusedIndex == index
                    val selectedWidth by animateDpAsState(
                        targetValue = if (isFocused) TopNavigationSelectedWidth else TopNavigationSlotWidth,
                        animationSpec = tween(durationMillis = 220),
                        label = "top-navigation-width",
                    )
                    Box(
                        modifier = Modifier
                            .width(TopNavigationSlotWidth)
                            .height(TopNavigationItemHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(selectedWidth)
                                .height(TopNavigationItemHeight)
                                .background(
                                    color = if (isFocused) {
                                        ApertureTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = TopNavigationItemShape,
                                )
                                .focusRequester(requesters[index])
                                .focusProperties {
                                    up = FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                }
                                .focusable()
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        focusedIndex = index
                                    }
                                }
                                .clickable { onDestinationClick(item.destination) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = isFocused,
                                transitionSpec = {
                                    fadeIn(tween(140)) togetherWith fadeOut(tween(90))
                                },
                                label = "top-navigation-content",
                            ) { expanded ->
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    NavigationIcon(
                                        destination = item.destination,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    if (expanded) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = item.label,
                                            fontFamily = ApertureBrandFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ApertureTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationIcon(
    destination: Destination,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        Destination.Home -> ApertureBrandMark(modifier = modifier)
        Destination.Shows -> Icon(Icons.Rounded.Tv, contentDescription = null, modifier = modifier)
        Destination.Movies -> Icon(Icons.Rounded.Movie, contentDescription = null, modifier = modifier)
        Destination.Search -> Icon(Icons.Rounded.Search, contentDescription = null, modifier = modifier)
        Destination.MyList -> Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, modifier = modifier)
        Destination.Settings -> Icon(Icons.Rounded.Settings, contentDescription = null, modifier = modifier)
        is Destination.Player -> Spacer(modifier)
    }
}
