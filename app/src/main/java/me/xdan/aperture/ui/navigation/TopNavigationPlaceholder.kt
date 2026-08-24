package me.xdan.aperture.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun TopNavigationPlaceholder(
    currentDestination: Destination,
    onDestinationClick: (Destination) -> Unit,
) {
    val destinations = listOf(
        Destination.Shows to "Shows",
        Destination.Movies to "Movies",
        Destination.Search to "Search",
        Destination.Home to "Aperture",
        Destination.MyList to "My List",
        Destination.Settings to "Settings",
    )

    Surface(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        shape = RoundedCornerShape(32.dp),
        colors = SurfaceDefaults.colors(
            containerColor = androidx.tv.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            contentColor = androidx.tv.material3.MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            destinations.forEachIndexed { index, (destination, label) ->
                Button(
                    onClick = { onDestinationClick(destination) },
                    modifier = Modifier.focusProperties {
                        up = FocusRequester.Cancel
                    },
                    colors = if (destination.focusKey() == currentDestination.focusKey()) {
                        ButtonDefaults.colors()
                    } else {
                        ButtonDefaults.colors(
                            containerColor = androidx.tv.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                            focusedContainerColor = androidx.tv.material3.MaterialTheme.colorScheme.secondaryContainer,
                            focusedContentColor = androidx.tv.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    },
                ) {
                    Text(label)
                }
            }
        }
    }
}
