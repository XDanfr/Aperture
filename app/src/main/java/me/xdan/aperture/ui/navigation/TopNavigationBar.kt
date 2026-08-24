package me.xdan.aperture.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.xdan.aperture.ui.component.ApertureBrandMark
import me.xdan.aperture.ui.theme.ApertureBrandFontFamily
import me.xdan.aperture.ui.theme.ApertureTheme

private val OuterShape = RoundedCornerShape(30.dp)
private val SelectedShape = RoundedCornerShape(28.dp)
private val OuterHeight = 56.dp
private val SlotWidth = 44.dp
private val SlotGap = 2.dp
private val SelectedWidths = listOf(92.dp, 100.dp, 96.dp, 126.dp, 102.dp, 108.dp)
private val OuterWidth = 362.dp
private val OuterSidePadding = 44.dp

private data class NavigationItem(
    val destination: Destination,
    val label: String,
)

@Composable
fun TopNavigationBar(
    currentDestination: Destination,
    onDestinationClick: (Destination) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
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
    var focusedIndex by remember {
        mutableIntStateOf(
            items.indexOfFirst { it.destination.focusKey() == currentDestination.focusKey() }.coerceAtLeast(0)
        )
    }

    LaunchedEffect(currentDestination) {
        val index = items.indexOfFirst { it.destination.focusKey() == currentDestination.focusKey() }
        if (index >= 0) focusedIndex = index
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .width(OuterWidth)
                .height(OuterHeight),
            shape = OuterShape,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val selectedWidth by animateDpAsState(
                    targetValue = SelectedWidths.getOrElse(focusedIndex) { 100.dp },
                    animationSpec = tween(240),
                    label = "top-navigation-selected-width",
                )
                val selectedX by animateDpAsState(
                    targetValue = OuterSidePadding + (SlotWidth + SlotGap) * focusedIndex - (selectedWidth - SlotWidth) / 2,
                    animationSpec = tween(240),
                    label = "top-navigation-selected-x",
                )

                Box(
                    modifier = Modifier
                        .offset(x = selectedX)
                        .width(selectedWidth)
                        .height(OuterHeight)
                        .background(
                            color = ApertureTheme.colorScheme.primaryContainer,
                            shape = SelectedShape,
                        ),
                )

                Row(
                    modifier = Modifier
                        .padding(horizontal = OuterSidePadding)
                        .height(OuterHeight),
                    horizontalArrangement = Arrangement.spacedBy(SlotGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        val requester = requesters[index]
                        val isFocused = focusedIndex == index

                        Box(
                            modifier = Modifier
                                .width(SlotWidth)
                                .height(OuterHeight)
                                .focusRequester(requester)
                                .focusProperties {
                                    up = FocusRequester.Cancel
                                }
                                .focusable()
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        focusedIndex = index
                                        onDestinationClick(item.destination)
                                        scope.launch {
                                            delay(120)
                                            runCatching { requester.requestFocus() }
                                        }
                                    }
                                }
                                .clickable {
                                    onDestinationClick(item.destination)
                                    scope.launch {
                                        delay(120)
                                        focusManager.moveFocus(FocusDirection.Down)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isFocused) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    NavigationIcon(item.destination, Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        fontFamily = ApertureBrandFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            } else {
                                NavigationIcon(item.destination, Modifier.size(24.dp))
                            }
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
    modifier: Modifier,
) {
    when (destination) {
        Destination.Home -> ApertureBrandMark(modifier = modifier)
        Destination.Shows -> Icon(androidx.compose.material.icons.Icons.Rounded.Tv, contentDescription = null, modifier = modifier)
        Destination.Movies -> Icon(androidx.compose.material.icons.Icons.Rounded.Movie, contentDescription = null, modifier = modifier)
        Destination.Search -> Icon(androidx.compose.material.icons.Icons.Rounded.Search, contentDescription = null, modifier = modifier)
        Destination.MyList -> Icon(androidx.compose.material.icons.Icons.Rounded.FavoriteBorder, contentDescription = null, modifier = modifier)
        Destination.Settings -> Icon(androidx.compose.material.icons.Icons.Rounded.Settings, contentDescription = null, modifier = modifier)
        is Destination.Player -> Unit
    }
}
