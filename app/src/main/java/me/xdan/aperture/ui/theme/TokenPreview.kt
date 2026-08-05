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

/**
 * A comprehensive showcase of Aperture's Material 3 Expressive token layer.
 *
 * This preview serves as the "design system homepage", allowing developers
 * to verify visual balance and token relationships at a glance.
 */
@Preview(device = "id:tv_1080p")
@Composable
fun ExpressiveShowcasePreview() {
    ApertureTheme {
        val tokens = ApertureTheme.tokens
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.colorScheme.background)
                .padding(tokens.spacing.huge),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.large)
        ) {
            Text(
                text = "Aperture Expressive Design System",
                style = MaterialTheme.typography.displayMedium,
                color = tokens.colorScheme.onBackground
            )

            // Hero Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(tokens.shapes.hero)
                    .background(tokens.colorScheme.heroBackground)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), tokens.shapes.hero),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Hero Banner Token",
                    style = MaterialTheme.typography.headlineLarge,
                    color = tokens.colorScheme.onSurface
                )
            }

            // Shelf Section
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.colorScheme.onBackground
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.medium)
            ) {
                items(5) { index ->
                    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.small)) {
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .height(240.dp)
                                .shadow(tokens.elevation.card, tokens.shapes.poster)
                                .clip(tokens.shapes.poster)
                                .background(if (index == 0) tokens.colorScheme.focusedMediaCardBackground else tokens.colorScheme.mediaCardBackground)
                        )
                        Text(
                            text = "Media Item $index",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colorScheme.onBackground
                        )
                    }
                }
            }

            // Controls & Dialog mockup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = tokens.spacing.large)
                        .clip(tokens.shapes.button)
                        .background(tokens.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Expressive Button",
                        color = tokens.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Loading indicator mockup
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .border(4.dp, tokens.colorScheme.secondary.copy(alpha = 0.2f), CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.25f)
                            .align(Alignment.TopCenter)
                            .background(tokens.colorScheme.secondary, CircleShape)
                    )
                }
            }
            
            // Playback Overlay mockup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(tokens.shapes.poster)
                    .background(tokens.colorScheme.playbackOverlay)
                    .padding(tokens.spacing.medium),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(tokens.spacing.medium)) {
                    Box(modifier = Modifier.size(40.dp).background(tokens.colorScheme.primary, CircleShape))
                    Box(modifier = Modifier.fillMaxWidth(0.6f).height(4.dp).background(tokens.colorScheme.onSurface.copy(alpha = 0.3f)))
                }
            }
        }
    }
}
