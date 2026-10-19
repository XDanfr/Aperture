package me.xdan.aperture.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.domain.model.AmbientModeType
import me.xdan.aperture.domain.model.AmbientSettings
import me.xdan.aperture.ui.screen.ambient.AmbientScene
import me.xdan.aperture.ui.screen.ambient.AmbientViewModel

@Composable
internal fun AmbientSettingsPreview(
    settings: AmbientSettings,
    modifier: Modifier = Modifier,
    media: List<MediaEntity>? = null
) {
    val ambientViewModel = if (media == null) viewModel<AmbientViewModel>() else null
    val library = ambientViewModel?.media?.collectAsState()?.value ?: media.orEmpty()
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.coerceAtLeast(1)
    val caption = (if (settings.mode == AmbientModeType.CINEMATIC) "Cinematic" else "Poster wall") +
        if (settings.showClock) ", clock shown" else ", clock hidden"
    BoxWithConstraints(
        modifier.aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clearAndSetSemantics { contentDescription = caption }
    ) {
        // Scale the real screen's dp geometry without allocating a full-screen rendering layer.
        val scale = maxWidth.value / screenWidth
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            AmbientScene(
                allMedia = library,
                settings = settings,
                animate = false,
                artworkAccent = { ambientViewModel?.artworkAccent(it) }
            )
        }
    }
}
