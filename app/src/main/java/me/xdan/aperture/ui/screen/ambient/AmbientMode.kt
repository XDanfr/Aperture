package me.xdan.aperture.ui.screen.ambient

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*

@Composable
fun AmbientMode() {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Aperture",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White.copy(alpha = alpha)
            )
            Text(
                text = "Ambient Mode",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray.copy(alpha = alpha)
            )
        }
    }
}
