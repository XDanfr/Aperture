package me.xdan.aperture.ui.screen.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import me.xdan.aperture.data.local.entity.ProfileEntity
import me.xdan.aperture.ui.platform.Dialog
import me.xdan.aperture.ui.theme.ApertureTheme
import me.xdan.aperture.ui.theme.ApertureThemeOption
import me.xdan.aperture.ui.theme.ApertureThemeOptions
import me.xdan.aperture.ui.theme.DynamicThemeId

/** Avatar colours: every named palette except Artwork Dynamic, which has no single colour. */
val ProfileColorOptions: List<ApertureThemeOption> = ApertureThemeOptions.filter { it.id != DynamicThemeId }

private const val MAX_PROFILE_NAME_LENGTH = 20

private fun colorOption(colorId: String): ApertureThemeOption =
    ProfileColorOptions.firstOrNull { it.id == colorId } ?: ProfileColorOptions.first()

/** The next colour nobody is using yet, for a new profile. */
fun nextProfileColor(profiles: List<ProfileEntity>): String {
    val used = profiles.map { it.colorId }.toSet()
    // Material TV's white reads as a blank avatar, so it's only suggested once the colours run out.
    val suggestions = ProfileColorOptions.filter { it.id != "classic" } + ProfileColorOptions.filter { it.id == "classic" }
    return (suggestions.firstOrNull { it.id !in used } ?: suggestions.first()).id
}

/** A profile's initial on its colour. */
@Composable
fun ProfileAvatar(name: String, colorId: String, size: Dp, modifier: Modifier = Modifier) {
    val option = colorOption(colorId)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(option.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color(0xFF1C1B1F),
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.42f).sp
        )
    }
}

/** Focus [requester] once the window (a dialog's, when there is one) has focus. */
@Composable
private fun RequestInitialFocus(requester: FocusRequester, key: Any? = Unit) {
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo.isWindowFocused, key) {
        if (!windowInfo.isWindowFocused) return@LaunchedEffect
        // A list's rows may not be laid out yet, so keep trying for a few frames.
        repeat(30) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
}

/** "Who's watching?" at launch when more than one profile exists. */
@Composable
fun ProfilePickerScreen(
    profiles: List<ProfileEntity>,
    lastProfileId: Long,
    onSelect: (Long) -> Unit
) {
    val initialRequester = remember { FocusRequester() }
    val initialId = profiles.firstOrNull { it.id == lastProfileId }?.id ?: profiles.firstOrNull()?.id
    if (profiles.isNotEmpty()) RequestInitialFocus(initialRequester, initialId)

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Who's watching?", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(48.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                contentPadding = PaddingValues(horizontal = 72.dp, vertical = 24.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileTile(
                        profile = profile,
                        onClick = { onSelect(profile.id) },
                        modifier = if (profile.id == initialId) Modifier.focusRequester(initialRequester) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTile(profile: ProfileEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(172.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f, pressedScale = 0.96f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(4.dp, MaterialTheme.colorScheme.onSurface),
                    inset = 6.dp,
                    shape = CircleShape
                )
            )
        ) {
            ProfileAvatar(profile.name, profile.colorId, 148.dp, Modifier.padding(10.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            profile.name,
            style = MaterialTheme.typography.titleLarge,
            color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/** Settings › Profiles: switch, add and edit. */
@Composable
fun ProfilesDialog(
    profiles: List<ProfileEntity>,
    activeProfileId: Long,
    onSwitch: (Long) -> Unit,
    onCreate: (name: String, colorId: String) -> Unit,
    onUpdate: (id: Long, name: String, colorId: String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val firstRequester = remember { FocusRequester() }
    // After adding someone, land on their Switch so switching is one press away.
    val newestRequester = remember { FocusRequester() }
    var focusNewest by remember { mutableStateOf(false) }
    val countBeforeAdding = remember { mutableIntStateOf(profiles.size) }

    when {
        adding -> ProfileEditorDialog(
            title = "Add profile",
            initialName = "",
            initialColorId = nextProfileColor(profiles),
            onSave = { name, color ->
                countBeforeAdding.intValue = profiles.size
                focusNewest = true
                onCreate(name, color)
                adding = false
            },
            onDelete = null,
            onDismiss = { adding = false }
        )
        editing != null -> {
            val profile = editing!!
            ProfileEditorDialog(
                title = "Edit profile",
                initialName = profile.name,
                initialColorId = profile.colorId,
                onSave = { name, color -> onUpdate(profile.id, name, color); editing = null },
                onDelete = if (profiles.size > 1) {
                    { onDelete(profile.id); editing = null }
                } else null,
                onDismiss = { editing = null }
            )
        }
        else -> Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val newestArrived = focusNewest && profiles.size > countBeforeAdding.intValue
            // Wait for the new row before focusing anything, or focus would land elsewhere first.
            if (!focusNewest || newestArrived) {
                val target = if (newestArrived) newestRequester else firstRequester
                RequestInitialFocus(target, target)
            }
            Surface(
                modifier = Modifier.width(660.dp).heightIn(max = 600.dp),
                shape = ApertureTheme.shapes.dialog,
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(ApertureTheme.spacing.huge)) {
                    Text("Profiles", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Each profile keeps its own My List, progress, hidden titles, theme and subtitle style.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                    Spacer(Modifier.height(ApertureTheme.spacing.large))
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small)
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            val active = profile.id == activeProfileId
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProfileAvatar(profile.name, profile.colorId, 48.dp)
                                Spacer(Modifier.width(ApertureTheme.spacing.medium))
                                Column(Modifier.weight(1f)) {
                                    Text(profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (active) {
                                        Text("Watching now", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                if (!active) {
                                    Button(
                                        onClick = { onSwitch(profile.id) },
                                        modifier = when {
                                            profile == profiles.last() -> Modifier.focusRequester(newestRequester)
                                            profile == profiles.first() -> Modifier.focusRequester(firstRequester)
                                            else -> Modifier
                                        }
                                    ) { Text("Switch") }
                                    Spacer(Modifier.width(ApertureTheme.spacing.small))
                                }
                                OutlinedButton(
                                    onClick = { focusNewest = false; editing = profile },
                                    modifier = if (active && profile == profiles.first()) Modifier.focusRequester(firstRequester) else Modifier
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Edit")
                                }
                            }
                        }
                        // A full-width row, so Down from any profile's buttons lands here before Done.
                        item(key = "add") {
                            OutlinedButton(
                                onClick = { adding = true },
                                modifier = Modifier.fillMaxWidth().padding(top = ApertureTheme.spacing.small)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add profile")
                            }
                        }
                    }
                    Spacer(Modifier.height(ApertureTheme.spacing.large))
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorDialog(
    title: String,
    initialName: String,
    initialColorId: String,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var colorId by remember { mutableStateOf(initialColorId) }
    var confirmDelete by remember { mutableStateOf(false) }
    val nameRequester = remember { FocusRequester() }
    val canSave = name.isNotBlank()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The dialog's own window: the one outside it would move focus behind the dialog.
        val focusManager = LocalFocusManager.current
        RequestInitialFocus(nameRequester)
        Surface(
            modifier = Modifier.width(660.dp),
            shape = ApertureTheme.shapes.dialog,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(ApertureTheme.spacing.huge)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(name.ifBlank { "?" }, colorId, 56.dp)
                    Spacer(Modifier.width(ApertureTheme.spacing.medium))
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(ApertureTheme.spacing.large))
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_PROFILE_NAME_LENGTH) },
                    label = { androidx.compose.material3.Text("Name") },
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    // A single-line field keeps D-pad Down for its cursor, which would trap a remote
                    // here once the keyboard closes. Down and the keyboard's Done move on to the colours.
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                focusManager.moveFocus(FocusDirection.Down)
                            } else false
                        }
                        .textInputFocusTracking()
                )
                Spacer(Modifier.height(ApertureTheme.spacing.large))
                Text("Colour", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(ApertureTheme.spacing.small))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    items(ProfileColorOptions, key = { it.id }) { option ->
                        ColorChoice(option, selected = option.id == colorId) { colorId = option.id }
                    }
                }
                Spacer(Modifier.height(ApertureTheme.spacing.large))
                if (confirmDelete && onDelete != null) {
                    Text(
                        "Delete ${initialName.ifBlank { "this profile" }}? Its My List, progress and settings will be removed.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(ApertureTheme.spacing.medium))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small)) {
                    if (onDelete != null) {
                        OutlinedButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                            Text(if (confirmDelete) "Delete profile" else "Delete")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { if (canSave) onSave(name.trim(), colorId) }, enabled = canSave) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun ColorChoice(option: ApertureThemeOption, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = option.primary, focusedContainerColor = option.primary),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), inset = 3.dp, shape = CircleShape) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), inset = 3.dp, shape = CircleShape)
        ),
        modifier = Modifier.size(52.dp)
    ) {
        if (selected) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = option.label, tint = Color(0xFF1C1B1F))
            }
        }
    }
}
