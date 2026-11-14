package me.xdan.aperture.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalWindowInfo
import me.xdan.aperture.ui.theme.ApertureTheme
import me.xdan.aperture.ui.theme.ApertureThemeOptions
import me.xdan.aperture.ui.theme.PhoneThemeId
import me.xdan.aperture.ui.theme.toThemeOption
import me.xdan.aperture.companion.PairedPhone
import me.xdan.aperture.ui.screen.companion.CompanionViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ThemePickerDialog(
    selectedThemeId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val firstRequester = remember { FocusRequester() }
    val companion: CompanionViewModel = hiltViewModel()
    val pairedPhones by companion.hub.pairedPhones.collectAsState()
    val themePhone by companion.hub.themePhone.collectAsState()
    // phones that reported Material You colours (Android 12+ companions).
    val colourPhones = remember(pairedPhones) { pairedPhones.filter { it.colors != null } }
    val phoneOption = remember(colourPhones, themePhone) {
        val phone = themePhone ?: colourPhones.singleOrNull()
        when {
            phone != null -> phone.colors!!.toThemeOption("Match ${phone.name}")
            colourPhones.isNotEmpty() -> colourPhones.first().colors!!.toThemeOption("Match a phone")
            else -> null
        }
    }
    val options = remember(phoneOption) { ApertureThemeOptions + listOfNotNull(phoneOption) }
    var choosingPhone by remember { mutableStateOf(false) }
    val select: (String) -> Unit = { id ->
        if (id != PhoneThemeId) {
            onSelect(id)
        } else if (colourPhones.size == 1) {
            // only one phone to match: no need to ask.
            companion.hub.setThemePhone(colourPhones.single().deviceId)
            onSelect(id)
        } else {
            choosingPhone = true
        }
    }
    val themeRows = remember(options) { options.chunked(3) }
    val initialThemeId = remember { options.firstOrNull { it.id == selectedThemeId }?.id ?: options.first().id }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = options.indexOfFirst { it.id == initialThemeId }.coerceAtLeast(0) / 3
    )

    if (choosingPhone) {
        PhoneThemeChooser(
            phones = colourPhones,
            selectedDeviceId = themePhone?.deviceId.takeIf { selectedThemeId == PhoneThemeId },
            onChoose = { phone ->
                companion.hub.setThemePhone(phone.deviceId)
                onSelect(PhoneThemeId)
                choosingPhone = false
            },
            onDismiss = { choosingPhone = false }
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val windowInfo = LocalWindowInfo.current
        LaunchedEffect(windowInfo.isWindowFocused) {
            if (!windowInfo.isWindowFocused) return@LaunchedEffect
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
            withFrameNanos { }
            withFrameNanos { }
            firstRequester.requestFocus()
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(820.dp).heightIn(max = 720.dp),
                shape = ApertureTheme.shapes.dialog,
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(ApertureTheme.spacing.huge)) {
                    Text("Choose a theme", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (phoneOption != null) "Choose a palette for Aperture, or match a paired phone's Material You colours."
                        else "Choose a palette for Aperture.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                    Spacer(Modifier.height(ApertureTheme.spacing.large))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            top = 12.dp,
                            bottom = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium)
                    ) {
                        items(
                            themeRows,
                            key = { row -> row.firstOrNull()?.id ?: "empty" }
                        ) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    ApertureTheme.spacing.medium
                                )
                            ) {
                                repeat(3) { index ->
                                    val option = row.getOrNull(index)

                                    if (option != null) {
                                        ThemeOptionCard(
                                            option = option,
                                            selected = option.id == selectedThemeId,
                                            firstRequester = if (
                                                option.id == initialThemeId
                                            ) firstRequester else null,
                                            onSelect = select,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(152.dp)
                                        )
                                    } else {
                                        Spacer(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(152.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(ApertureTheme.spacing.medium))
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
                }
            }
        }
    }
}

/** picks which paired phone the "Match phone" theme follows, previewing each one's colours. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PhoneThemeChooser(
    phones: List<PairedPhone>,
    selectedDeviceId: String?,
    onChoose: (PairedPhone) -> Unit,
    onDismiss: () -> Unit
) {
    val requester = remember { FocusRequester() }
    val initial = phones.firstOrNull { it.deviceId == selectedDeviceId } ?: phones.firstOrNull()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val windowInfo = LocalWindowInfo.current
        LaunchedEffect(windowInfo.isWindowFocused) {
            if (!windowInfo.isWindowFocused) return@LaunchedEffect
            withFrameNanos { }
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(820.dp),
                shape = ApertureTheme.shapes.dialog,
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(ApertureTheme.spacing.huge)) {
                    Text("Match which phone?", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Aperture will use that phone's Material You colours.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                    Spacer(Modifier.height(ApertureTheme.spacing.large))
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.medium),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(phones, key = { it.deviceId }) { phone ->
                            ThemeOptionCard(
                                option = phone.colors!!.toThemeOption(phone.name),
                                selected = phone.deviceId == selectedDeviceId,
                                firstRequester = if (phone == initial) requester else null,
                                onSelect = { onChoose(phone) },
                                modifier = Modifier.width(236.dp).height(152.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(ApertureTheme.spacing.medium))
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ThemeOptionCard(
    option: me.xdan.aperture.ui.theme.ApertureThemeOption,
    selected: Boolean,
    firstRequester: FocusRequester?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onSelect(option.id) },
        modifier = modifier
            .then(
                if (firstRequester != null) {
                    Modifier.focusRequester(firstRequester)
                } else {
                    Modifier
                }
            ),
        shape = ClickableSurfaceDefaults.shape(ApertureTheme.shapes.poster),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1.025f,
            pressedScale = 0.98f
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary
                ),
                shape = ApertureTheme.shapes.poster
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ThemeColourSwatch(
                option = option,
                selected = selected
            )

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.label,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }


        }
    }
}

@Composable
private fun ThemeColourSwatch(
    option: me.xdan.aperture.ui.theme.ApertureThemeOption,
    selected: Boolean
) {
    val outlineColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }

    ApertureThemeSwatch(
        option = option,
        outlineColor = outlineColor,
        outlineWidth = if (selected) 3.dp else 1.dp,
        modifier = Modifier.size(88.dp)
    )
}
