package me.xdan.aperture.ui.screen.onboarding

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import me.xdan.aperture.ui.component.expressive.ExpressiveLoadingIndicator
import me.xdan.aperture.ui.component.expressive.ExpressiveProgressIndicator
import me.xdan.aperture.ui.theme.ApertureTheme

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    progress: LibraryPreparationProgress,
    onStartPreparation: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit,
    rescanMode: Boolean = false,
    onRescanComplete: () -> Unit = onComplete
) {
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
        if (progress.stage == LibraryPreparationStage.COMPLETE) {
            if (rescanMode && !rescanHasStarted) return@LaunchedEffect
            delay(700)
            if (rescanMode) onRescanComplete() else onComplete()
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
            PreparationPanel(
                progress = progress,
                onRetry = onStartPreparation,
                onSkip = onSkip,
                rescanMode = rescanMode,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            PermissionPanel(
                onGrantPermission = permissionState::launchPermissionRequest,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PermissionPanel(
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.72f)),
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
    rescanMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDiscovering = progress.stage == LibraryPreparationStage.IDLE ||
        progress.stage == LibraryPreparationStage.DISCOVERING
    val isMatching = progress.stage == LibraryPreparationStage.MATCHING
    val isError = progress.stage == LibraryPreparationStage.ERROR
    val isComplete = progress.stage == LibraryPreparationStage.COMPLETE

    val skipFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isComplete) {
        if (!isComplete) {
            repeat(15) {
                delay(100)
                if (runCatching { skipFocusRequester.requestFocus() }.getOrDefault(false)) {
                    return@LaunchedEffect
                }
            }
        }
    }

    Surface(
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        shape = ApertureTheme.shapes.dialog,
        modifier = modifier.width(620.dp)
    ) {
        Column(modifier = Modifier.padding(ApertureTheme.spacing.huge)) {
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
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator(size = 40.dp)
                }
            } else {
                ExpressiveProgressIndicator(
                    progress = progress.totalProgress.coerceIn(0f, 1f)
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
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveLoadingIndicator(size = 36.dp)
                }
            } else {
                ExpressiveProgressIndicator(
                    progress = progress.currentItemProgress.coerceIn(0f, 1f)
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
                if (!isComplete && !rescanMode) {
                    Button(
                        onClick = onSkip,
                        modifier = Modifier.focusRequester(skipFocusRequester)
                    ) { Text("Skip") }
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
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        if (showLoading) {
            ExpressiveLoadingIndicator(size = 24.dp)
            Spacer(Modifier.width(8.dp))
        }
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
