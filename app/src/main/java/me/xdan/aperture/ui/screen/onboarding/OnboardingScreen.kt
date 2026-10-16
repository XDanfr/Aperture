package me.xdan.aperture.ui.screen.onboarding

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import me.xdan.aperture.R
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.domain.repository.LibraryPreparationProgress
import me.xdan.aperture.domain.repository.LibraryPreparationStage
import me.xdan.aperture.ui.screen.settings.AmbientSettingsDialog
import me.xdan.aperture.domain.model.AmbientSettings
import me.xdan.aperture.domain.model.AmbientModeType
import me.xdan.aperture.ui.theme.ApertureThemeOptions
import me.xdan.aperture.ui.component.ThemePickerDialog
import me.xdan.aperture.ui.component.expressive.ExpressiveWavyProgressIndicator
import me.xdan.aperture.ui.theme.ApertureTheme

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    progress: LibraryPreparationProgress,
    onStartPreparation: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit,
    rescanMode: Boolean = false,
    onRescanComplete: () -> Unit = onComplete,
    selectedThemeId: String = "purple",
    onSelectTheme: (String) -> Unit = {},
    ambientSettings: AmbientSettings = AmbientSettings(),
    onSaveAmbient: (AmbientSettings) -> Unit = {},
    onLearn: () -> Unit = onComplete
) {
    var showThemePicker by remember { mutableStateOf(false) }
    var showAmbientSettings by remember { mutableStateOf(false) }
    val themeRequester = remember { FocusRequester() }
    val ambientRequester = remember { FocusRequester() }
    var pendingFocus by remember { mutableStateOf<FocusRequester?>(null) }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(pendingFocus, windowInfo.isWindowFocused) {
        if (!windowInfo.isWindowFocused) return@LaunchedEffect
        val requester = pendingFocus ?: return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        requester.requestFocus()
        pendingFocus = null
    }
    val permissionState = rememberPermissionState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )
    val hasLibraryAccess = permissionState.status.isGranted
    var rescanHasStarted by remember(rescanMode) { mutableStateOf(!rescanMode) }

    LaunchedEffect(hasLibraryAccess) {
        if (hasLibraryAccess) onStartPreparation()
    }

    LaunchedEffect(progress.stage) {
        if (rescanMode && progress.stage in setOf(
                LibraryPreparationStage.DISCOVERING,
                LibraryPreparationStage.MATCHING
            )) {
            rescanHasStarted = true
        }
        if (rescanMode && progress.stage == LibraryPreparationStage.COMPLETE) {
            if (rescanMode && !rescanHasStarted) return@LaunchedEffect
            delay(700)
            onRescanComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (rescanMode || hasLibraryAccess) {
            PosterBackground(progress.posterPaths)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.68f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                            )
                        )
                    )
            )
            if (rescanMode) {
                PreparationPanel(
                    progress = progress,
                    onRetry = onStartPreparation,
                    onSkip = onSkip,
                    onComplete = onComplete,
                    onLearn = onLearn,
                    rescanMode = true,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(ApertureTheme.spacing.large).align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreparationPanel(
                        progress = progress,
                        onRetry = onStartPreparation,
                        onSkip = onSkip,
                        onComplete = onComplete,
                        onLearn = onLearn,
                        focusEnabled = !showThemePicker && !showAmbientSettings,
                        modifier = Modifier.weight(1.5f).heightIn(max = 480.dp)
                    )
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = ApertureTheme.shapes.dialog,
                        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Column(
                            Modifier.padding(ApertureTheme.spacing.large),
                            verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)
                        ) {
                            Text("Customise Aperture", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Choose your theme and what to show while Aperture is idle. You can change these later in Settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { showThemePicker = true },
                                modifier = Modifier.fillMaxWidth().focusRequester(themeRequester)
                            ) {
                                Column {
                                    Text("Theme")
                                    Text(
                                        ApertureThemeOptions.firstOrNull { it.id == selectedThemeId }?.label ?: "Aperture Purple",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { showAmbientSettings = true },
                                modifier = Modifier.fillMaxWidth().focusRequester(ambientRequester)
                            ) {
                                Column {
                                    Text("Ambient mode")
                                    Text(
                                        (if (ambientSettings.mode == AmbientModeType.CINEMATIC) "Cinematic" else "Poster wall") +
                                            if (ambientSettings.showClock) " · Clock on" else " · Clock off",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            PermissionPanel(
                onGrantPermission = permissionState::launchPermissionRequest,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
    if (showThemePicker) {
        ThemePickerDialog(
            selectedThemeId = selectedThemeId,
            onSelect = onSelectTheme,
            onDismiss = {
                showThemePicker = false
                pendingFocus = themeRequester
            }
        )
    }
    if (showAmbientSettings) {
        AmbientSettingsDialog(
            initial = ambientSettings,
            onSave = {
                onSaveAmbient(it)
                showAmbientSettings = false
                pendingFocus = ambientRequester
            },
            onDismiss = {
                showAmbientSettings = false
                pendingFocus = ambientRequester
            }
        )
    }

}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PermissionPanel(
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.72f), contentColor = MaterialTheme.colorScheme.onSurface),
        shape = ApertureTheme.shapes.dialog,
        modifier = modifier.width(540.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(ApertureTheme.spacing.huge)
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(id = R.drawable.ic_banner_foreground),
                contentDescription = null,
                modifier = Modifier.size(300.dp),
                tint = Color.Unspecified
            )
            Text(
                text = "Allow access to videos on this device to build your Aperture library.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onGrantPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Allow device videos")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PreparationPanel(
    progress: LibraryPreparationProgress,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit,
    onLearn: () -> Unit,
    rescanMode: Boolean = false,
    focusEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isDiscovering = progress.stage == LibraryPreparationStage.IDLE ||
        progress.stage == LibraryPreparationStage.DISCOVERING
    val isMatching = progress.stage == LibraryPreparationStage.MATCHING
    val isError = progress.stage == LibraryPreparationStage.ERROR
    val isComplete = progress.stage == LibraryPreparationStage.COMPLETE

    val skipFocusRequester = remember { FocusRequester() }
    val currentFocusEnabled by rememberUpdatedState(focusEnabled)
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        if (!rescanMode && currentFocusEnabled) skipFocusRequester.requestFocus()
    }

    Surface(
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = ApertureTheme.shapes.dialog,
        modifier = modifier.width(620.dp)
    ) {
        Column(modifier = Modifier.padding(ApertureTheme.spacing.large).verticalScroll(rememberScrollState())) {
            Text(
                text = when {
                    isError -> "We hit a snag"
                    isComplete -> if (rescanMode) "Rescan complete" else "Your library is ready"
                    isDiscovering -> if (rescanMode) "Scanning your library" else "Finding your media"
                    else -> "Building your library"
                },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(ApertureTheme.spacing.small))
            Text(
                text = when {
                    isError -> progress.errorMessage ?: "Aperture could not finish matching metadata."
                    isComplete && progress.errorMessage != null -> if (rescanMode) "The library was updated, but some titles could not be matched yet." else "Your library is ready, but some titles could not be matched yet. Aperture can retry them later."
                    isComplete -> "Everything we found has been added."
                    isDiscovering -> "Scanning local storage before matching titles with TMDB…"
                    else -> "Matching artwork and information with TMDB. You can skip and let this continue in the background."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f)
            )

            Spacer(Modifier.height(ApertureTheme.spacing.large))
            ProgressLabel(
                label = "Total progress",
                value = if (isDiscovering) "Scanning" else "${progress.completedItems} / ${progress.totalItems}"
            )
            Spacer(Modifier.height(ApertureTheme.spacing.small))
            if (isDiscovering) {
                ExpressiveWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                ExpressiveWavyProgressIndicator(
                    progress = progress.totalProgress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(ApertureTheme.spacing.medium))
            ProgressLabel(
                label = progress.currentTitle ?: if (isComplete) "Finished" else "Current title",
                value = if (progress.currentTitle == null) "" else "${(progress.currentItemProgress * 100).toInt()}%",
                showLoading = isMatching && !isComplete
            )
            Spacer(Modifier.height(ApertureTheme.spacing.small))
            if (isMatching && !isComplete) {
                ExpressiveWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                ExpressiveWavyProgressIndicator(
                    progress = progress.currentItemProgress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(ApertureTheme.spacing.huge))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isError) {
                    OutlinedButton(onClick = onRetry) { Text("Try again") }
                    Spacer(Modifier.width(ApertureTheme.spacing.medium))
                }
                if (!rescanMode) {
                    if (isComplete) {
                        OutlinedButton(onClick = onLearn) { Text("Learn Aperture") }
                        Spacer(Modifier.width(ApertureTheme.spacing.small))
                    }
                    Button(
                        onClick = if (isComplete) onComplete else onSkip,
                        modifier = Modifier.focusRequester(skipFocusRequester)
                    ) { Text(if (isComplete) "Get started" else "Skip") }
                }
            }
        }
    }
}

@Composable
private fun ProgressLabel(label: String, value: String, showLoading: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(ApertureTheme.spacing.small))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PosterBackground(posterPaths: List<String>) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(130.dp),
        modifier = Modifier.fillMaxSize().alpha(0.55f),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        items(posterPaths.takeLast(24), key = { it }) { posterPath ->
            var visible by remember(posterPath) { mutableStateOf(false) }
            LaunchedEffect(posterPath) { visible = true }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(durationMillis = 650))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(TmdbApi.IMAGE_BASE_URL + "w342" + posterPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }
        }
    }
}
