package me.xdan.aperture.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.ui.component.expressive.ExpressiveLoadingIndicator
import me.xdan.aperture.ui.component.expressive.ExpressiveToggle

/**
 * A comprehensive showcase of Aperture's Material 3 Expressive design system.
 *
 * This preview serves as the "design system homepage", allowing developers
 * to verify visual balance and token relationships at a glance.
 */
@Preview(device = "id:tv_1080p")
@Composable
fun ExpressiveShowcasePreview() {
    ApertureTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ApertureTheme.colorScheme.background)
                .padding(ApertureTheme.spacing.huge),
            verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large),
        ) {
            Text(
                text = "Aperture Expressive Design System",
                style = MaterialTheme.typography.displayMedium,
                color = ApertureTheme.colorScheme.onBackground
            )

            // Hero Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(ApertureTheme.shapes.hero)
                    .background(ApertureTheme.colorScheme.heroBackground)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), ApertureTheme.shapes.hero),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Hero Banner Token",
                    style = MaterialTheme.typography.headlineLarge,
                    color = ApertureTheme.colorScheme.onSurface
                )
            }

            // Shelf Section
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.titleLarge,
                color = ApertureTheme.colorScheme.onBackground
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)
            ) {
                items(5) { index ->
                    Column(verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small)) {
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .height(240.dp)
                                .shadow(ApertureTheme.elevation.card, ApertureTheme.shapes.poster)
                                .clip(ApertureTheme.shapes.poster)
                                .background(if (index == 0) ApertureTheme.colorScheme.focusedMediaCardBackground else ApertureTheme.colorScheme.mediaCardBackground)
                        )
                        Text(
                            text = "Media Item $index",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ApertureTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // Controls & Dialog mockup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = ApertureTheme.spacing.large)
                        .clip(ApertureTheme.shapes.button)
                        .background(ApertureTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Expressive Button",
                        color = ApertureTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Expressive Toggle
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ExpressiveToggle(checked = true, onCheckedChange = null)
                    Text("Expressive Toggle", style = MaterialTheme.typography.labelLarge)
                }

                // Loading indicator
                ExpressiveLoadingIndicator()
            }
            
            // Playback Overlay mockup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(ApertureTheme.shapes.poster)
                    .background(ApertureTheme.colorScheme.playbackOverlay)
                    .padding(ApertureTheme.spacing.medium),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)) {
                    Box(modifier = Modifier.size(40.dp).background(ApertureTheme.colorScheme.primary, CircleShape))
                    Box(modifier = Modifier.fillMaxWidth(0.6f).height(4.dp).background(ApertureTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
                }
            }
        }
    }
}
