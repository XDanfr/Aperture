package me.xdan.aperture.mobile.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import me.xdan.aperture.mobile.ui.components.deviceIcon
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.xdan.aperture.mobile.BuildConfig
import me.xdan.aperture.mobile.data.ConnectionState
import me.xdan.aperture.mobile.data.ThemeChoice
import me.xdan.aperture.mobile.ui.CompanionViewModel
import me.xdan.aperture.mobile.ui.theme.PhoneThemeOptions
import me.xdan.aperture.mobile.ui.theme.brandPalette
import me.xdan.aperture.mobile.ui.theme.supportsMaterialYou
import me.xdan.aperture.ui.component.ApertureBrandArtwork
import me.xdan.aperture.ui.component.ApertureThemeSwatch
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import me.xdan.aperture.ui.component.ApertureBrandPose
import me.xdan.aperture.ui.theme.ApertureThemeOptions
import me.xdan.aperture.ui.theme.DynamicThemeId

@Composable
private fun BrandHero(modifier: Modifier = Modifier) {
    // the shared Aperture mark, assembling once like the TV's launch animation.
    val assembly = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { assembly.animateTo(1f, me.xdan.aperture.mobile.ui.theme.CompanionMotion.brandSlowSpatial()) }
    ApertureBrandArtwork(
        modifier = modifier,
        palette = MaterialTheme.colorScheme.brandPalette(),
        pose = { ApertureBrandPose(assembly = assembly.value, rotation = (1f - assembly.value) * -90f) }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(viewModel: CompanionViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var paired by remember { mutableStateOf(false) }
    LaunchedEffect(paired) {
        if (paired) {
            delay(900)
            viewModel.completeOnboarding()
        }
    }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            AnimatedContent(targetState = page, label = "onboarding") { current ->
                if (current == 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        BrandHero(Modifier.size(160.dp))
                        Text("Aperture Companion", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                        Text(
                            "Browse your TV's library, start something on the big screen, and control playback from your phone. " +
                                "Your videos stay on the TV; only details and artwork come to your phone.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { page = 1 },
                            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
                        ) { Text("Get started", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)) }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("Pair with your TV", style = MaterialTheme.typography.headlineMedium)
                        if (paired) {
                            Text("Paired! Loading your library…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        } else {
                            PairingFlow(viewModel, onPaired = { paired = true })
                            TextButton(onClick = viewModel::completeOnboarding, modifier = Modifier.align(Alignment.End)) {
                                Text("Skip for now")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** shown on browse tabs when there's no paired TV yet. */
@Composable
fun NotPairedPrompt(onPair: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BrandHero(Modifier.size(120.dp))
        Text("No TV paired", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pair with Aperture on your TV to browse and control it from here.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onPair) { Text("Pair a TV") }
    }
}

@Composable
fun SettingsScreen(
    viewModel: CompanionViewModel,
    contentPadding: PaddingValues,
    onPair: () -> Unit
) {
    val choice by viewModel.themeChoice.collectAsState()
    val tv by viewModel.pairedTv.collectAsState()
    val tvState by viewModel.tvState.collectAsState()
    val connection by viewModel.connection.collectAsState()
    var confirmForget by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 8.dp))

        SettingsSection("TV") {
            val current = tv
            if (current == null) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(Icons.Rounded.Tv, null) },
                    headlineContent = { Text("No TV paired") },
                    trailingContent = { Button(onClick = onPair) { Text("Pair") } }
                )
            } else {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(deviceIcon(tvState?.deviceKind ?: current.deviceKind), null) },
                    headlineContent = { Text(tvState?.tvName ?: current.name) },
                    supportingContent = {
                        Text(
                            when (val c = connection) {
                                ConnectionState.Connected -> "Connected · ${current.host}"
                                ConnectionState.Connecting -> "Connecting…"
                                is ConnectionState.Offline -> "Offline · retrying in ${c.retryInSeconds}s"
                                ConnectionState.Revoked -> "This phone was removed on the TV"
                                ConnectionState.NotPaired -> "Not paired"
                            }
                        )
                    }
                )
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPair) { Text("Pair a different TV") }
                    TextButton(onClick = { confirmForget = true }) {
                        Icon(Icons.Rounded.LinkOff, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Unpair")
                    }
                }
            }
        }

        SettingsSection("Theme") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeRow(
                    selected = choice == ThemeChoice.SYNC_TV,
                    icon = Icons.Rounded.Sync,
                    title = "Match TV",
                    subtitle = tvState?.let { state ->
                        when {
                            state.themeId == DynamicThemeId -> "TV uses Artwork Dynamic · Aperture Purple here"
                            state.themeId == me.xdan.aperture.ui.theme.PhoneThemeId && state.themeDeviceId == viewModel.deviceId ->
                                "TV matches this phone · Material You"
                            state.themeId == me.xdan.aperture.ui.theme.PhoneThemeId -> "TV matches another phone's colours"
                            else -> ApertureThemeOptions.firstOrNull { it.id == state.themeId }?.label
                        }
                    } ?: "Follows the TV's theme",
                    onClick = { viewModel.setThemeChoice(ThemeChoice.SYNC_TV) }
                )
                if (supportsMaterialYou()) {
                    ThemeRow(
                        selected = choice == ThemeChoice.MATERIAL_YOU,
                        icon = Icons.Rounded.Wallpaper,
                        title = "Material You",
                        subtitle = "Colours from your wallpaper, light or dark with your phone",
                        onClick = { viewModel.setThemeChoice(ThemeChoice.MATERIAL_YOU) }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Aperture palettes", style = MaterialTheme.typography.titleSmall)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhoneThemeOptions.forEach { option ->
                        val selected = choice == option.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(76.dp).clip(MaterialTheme.shapes.medium)
                                .selectable(selected = selected, role = Role.RadioButton) { viewModel.setThemeChoice(option.id) }
                                .padding(4.dp)
                        ) {
                            // the TV's three-colour swatch, with a separated ring when selected
                            // so it stays visible on a swatch of the same colour.
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .then(
                                        if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                                    .padding(4.dp)
                            ) {
                                ApertureThemeSwatch(
                                    option = option,
                                    outlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        SettingsSection("About") {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Aperture Companion ${BuildConfig.VERSION_NAME}") },
                supportingContent = {
                    Text("Artwork from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.")
                }
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Unpair this TV?") },
            text = { Text("You'll need to scan the TV's code again to reconnect.") },
            confirmButton = {
                TextButton(onClick = { confirmForget = false; viewModel.forgetTv() }) { Text("Unpair") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
            Column { content() }
        }
    }
}

@Composable
private fun ThemeRow(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            if (selected) Icon(Icons.Rounded.Check, null)
        }
    }
}

/** full-screen pairing reached from Settings or empty states. */
@Composable
fun PairingPage(viewModel: CompanionViewModel, onDone: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onDone)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text("Pair with your TV", style = MaterialTheme.typography.headlineSmall)
            }
            PairingFlow(viewModel, onPaired = onDone)
        }
    }
}
