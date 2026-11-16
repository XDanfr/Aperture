package me.xdan.aperture.ui.screen.jellyfin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import me.xdan.aperture.data.jellyfin.JellyfinLibraryWatcher
import me.xdan.aperture.data.local.entity.JellyfinAccountEntity
import me.xdan.aperture.domain.model.LibraryMode
import me.xdan.aperture.ui.component.TvTextField
import me.xdan.aperture.ui.component.expressive.ExpressiveLoadingIndicator
import me.xdan.aperture.ui.platform.Dialog
import me.xdan.aperture.ui.theme.ApertureTheme

/** Settings › Jellyfin. Signs the active profile in, or manages its connection once it is. */
@Composable
fun JellyfinDialog(
    viewModel: JellyfinViewModel,
    onDismiss: () -> Unit
) {
    val account by viewModel.account.collectAsState()
    val setup by viewModel.setup.collectAsState()
    val mode by viewModel.libraryMode.collectAsState()
    val sync by viewModel.syncState.collectAsState()
    val titles by viewModel.indexedTitles.collectAsState()

    // Not signed in and not yet looking: start by finding servers.
    LaunchedEffect(account == null) {
        if (account == null && viewModel.setup.value == null) viewModel.startSetup()
    }

    val dismiss = {
        viewModel.cancelSetup()
        onDismiss()
    }

    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.width(720.dp).heightIn(max = 640.dp),
            shape = ApertureTheme.shapes.dialog,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(ApertureTheme.spacing.huge)) {
                when (val step = setup) {
                    JellyfinViewModel.Setup.LocalNetworkPermission -> LocalNetwork(viewModel, dismiss)
                    is JellyfinViewModel.Setup.FindServer -> FindServer(step, viewModel::connect, dismiss)
                    is JellyfinViewModel.Setup.Connecting -> Busy("Connecting to ${step.address}…", dismiss)
                    is JellyfinViewModel.Setup.QuickConnect -> QuickConnect(step, viewModel::usePassword, dismiss)
                    is JellyfinViewModel.Setup.Password -> PasswordSignIn(step, viewModel::signInWithPassword, viewModel::startSetup)
                    is JellyfinViewModel.Setup.ChooseMode -> ChooseMode(step.account, viewModel::chooseMode)
                    null -> account?.let { Connected(it, mode, sync, titles, viewModel, dismiss) }
                        ?: Busy("Looking for servers…", dismiss)
                }
            }
        }
    }
}

@Composable
private fun Heading(title: String, body: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    if (body != null) {
        Spacer(Modifier.height(ApertureTheme.spacing.small))
        Text(body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
    }
    Spacer(Modifier.height(ApertureTheme.spacing.large))
}

/** Focuses [requester] once the dialog window has focus, retrying until it's laid out. */
@Composable
private fun FocusOnShow(requester: FocusRequester, key: Any? = Unit) {
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo.isWindowFocused, key) {
        if (!windowInfo.isWindowFocused) return@LaunchedEffect
        repeat(30) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
}

@Composable
private fun LocalNetwork(viewModel: JellyfinViewModel, onCancel: () -> Unit) {
    val hub: me.xdan.aperture.companion.CompanionHub = org.koin.compose.koinInject()
    val allow = remember { FocusRequester() }
    FocusOnShow(allow)
    val request = me.xdan.aperture.ui.platform.rememberLocalNetworkPermission { granted ->
        // The phone companion needs the same access, so give it another go too.
        if (granted) hub.start()
        viewModel.onLocalNetworkResult(granted)
    }
    Heading(
        "Allow local network access",
        "Aperture needs to reach devices on your network to find and connect to a Jellyfin server at home."
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small, Alignment.End)) {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
        OutlinedButton(onClick = viewModel::enterAddressInstead) { Text("Enter an address instead") }
        Button(onClick = request, modifier = Modifier.focusRequester(allow)) { Text("Allow") }
    }
}

@Composable
private fun ColumnScope.FindServer(
    step: JellyfinViewModel.Setup.FindServer,
    onConnect: (String) -> Unit,
    onCancel: () -> Unit
) {
    var address by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    FocusOnShow(first, step.servers.size to step.searching)

    Heading(
        "Connect to Jellyfin",
        "Choose a server on your network, or enter its address. Aperture needs Jellyfin 12 or newer."
    )
    if (step.error != null) {
        Text(step.error, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(ApertureTheme.spacing.medium))
    }
    LazyColumn(
        Modifier.weight(1f, fill = false),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small)
    ) {
        items(step.servers, key = { it.id }) { server ->
            ListItem(
                selected = false,
                onClick = { onConnect(server.address) },
                headlineContent = { Text(server.name) },
                supportingContent = { Text(server.address) },
                leadingContent = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                modifier = if (server == step.servers.first()) Modifier.focusRequester(first) else Modifier
            )
        }
        if (step.searching) {
            item(key = "searching") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    ExpressiveLoadingIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(ApertureTheme.spacing.medium))
                    Text("Looking for servers on your network…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (step.servers.isEmpty() && step.error == null) {
            item(key = "none") {
                Text("No servers answered. Enter the address instead.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(ApertureTheme.spacing.large))
    TvTextField(
        value = address,
        onValueChange = { address = it },
        label = "Server address, e.g. 192.168.1.20:8096",
        keyboardType = KeyboardType.Uri,
        imeAction = ImeAction.Go,
        onSubmit = { onConnect(address) },
        modifier = if (step.servers.isEmpty()) Modifier.focusRequester(first) else Modifier
    )
    Spacer(Modifier.height(ApertureTheme.spacing.large))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small, Alignment.End)) {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
        Button(onClick = { onConnect(address) }, enabled = address.isNotBlank()) { Text("Connect") }
    }
}

@Composable
private fun ColumnScope.Busy(message: String, onCancel: () -> Unit) {
    val cancel = remember { FocusRequester() }
    FocusOnShow(cancel)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
        ExpressiveLoadingIndicator(modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(ApertureTheme.spacing.large))
        Text(message, style = MaterialTheme.typography.titleMedium)
    }
    OutlinedButton(onClick = onCancel, modifier = Modifier.align(Alignment.End).focusRequester(cancel)) { Text("Cancel") }
}

@Composable
private fun ColumnScope.QuickConnect(
    step: JellyfinViewModel.Setup.QuickConnect,
    onUsePassword: () -> Unit,
    onCancel: () -> Unit
) {
    val password = remember { FocusRequester() }
    FocusOnShow(password)
    Heading(
        "Sign in to ${step.serverName}",
        "On a phone or computer that's signed in to Jellyfin, open your profile, choose Quick Connect and enter this code."
    )
    Text(
        step.code.chunked(3).joinToString(" "),
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 6.sp,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = ApertureTheme.spacing.large)
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        ExpressiveLoadingIndicator(modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(ApertureTheme.spacing.small))
        Text("Waiting for approval…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(ApertureTheme.spacing.huge))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small, Alignment.End)) {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
        Button(onClick = onUsePassword, modifier = Modifier.focusRequester(password)) { Text("Use a password instead") }
    }
}

@Composable
private fun PasswordSignIn(
    step: JellyfinViewModel.Setup.Password,
    onSignIn: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    FocusOnShow(first)
    Heading("Sign in to ${step.serverName}", step.address)
    TvTextField(
        value = username,
        onValueChange = { username = it },
        label = "Username",
        enabled = !step.busy,
        modifier = Modifier.focusRequester(first)
    )
    Spacer(Modifier.height(ApertureTheme.spacing.medium))
    TvTextField(
        value = password,
        onValueChange = { password = it },
        label = "Password",
        password = true,
        enabled = !step.busy,
        imeAction = ImeAction.Go,
        onSubmit = { onSignIn(username, password) }
    )
    if (step.error != null) {
        Spacer(Modifier.height(ApertureTheme.spacing.small))
        Text(step.error, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(ApertureTheme.spacing.large))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small, Alignment.End)) {
        OutlinedButton(onClick = onBack, enabled = !step.busy) { Text("Back") }
        Button(onClick = { onSignIn(username, password) }, enabled = !step.busy && username.isNotBlank()) {
            Text(if (step.busy) "Signing in…" else "Sign in")
        }
    }
}

private data class ModeOption(val mode: LibraryMode, val title: String, val body: String)

private val ModeOptions = listOf(
    ModeOption(LibraryMode.JELLYFIN, "Jellyfin only", "Browse and play what's on the server. Aperture works as a Jellyfin client."),
    ModeOption(LibraryMode.HYBRID, "Jellyfin and this device", "Show both. Titles you have in both places appear once, and you choose which copy to play."),
    ModeOption(LibraryMode.LOCAL, "This device only", "Stay signed in, but only show local files.")
)

@Composable
private fun ChooseMode(account: JellyfinAccountEntity, onChoose: (LibraryMode) -> Unit) {
    val first = remember { FocusRequester() }
    FocusOnShow(first)
    Heading("Signed in as ${account.userName}", "How should this profile use ${account.serverName}? You can change this later in Settings.")
    ModeOptions.filter { it.mode != LibraryMode.LOCAL }.forEachIndexed { index, option ->
        ModeCard(option, selected = false, onClick = { onChoose(option.mode) }, modifier = if (index == 0) Modifier.focusRequester(first) else Modifier)
        Spacer(Modifier.height(ApertureTheme.spacing.small))
    }
}

@Composable
private fun ModeCard(option: ModeOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = { Text(option.title) },
        supportingContent = { Text(option.body) },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun Connected(
    account: JellyfinAccountEntity,
    mode: LibraryMode,
    sync: JellyfinLibraryWatcher.State,
    titles: Int,
    viewModel: JellyfinViewModel,
    onDone: () -> Unit
) {
    val first = remember { FocusRequester() }
    FocusOnShow(first)
    Heading(account.serverName, "Signed in as ${account.userName} · ${account.baseUrl}")
    Text("Library", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(ApertureTheme.spacing.small))
    ModeOptions.forEach { option ->
        ModeCard(
            option,
            selected = option.mode == mode,
            onClick = { viewModel.setLibraryMode(option.mode) },
            modifier = if (option.mode == mode) Modifier.focusRequester(first) else Modifier
        )
        Spacer(Modifier.height(ApertureTheme.spacing.extraSmall))
    }
    Spacer(Modifier.height(ApertureTheme.spacing.medium))
    Text(
        when (sync) {
            JellyfinLibraryWatcher.State.Idle -> if (mode.usesJellyfin) "Waiting to sync" else "Not syncing while this profile is local only"
            JellyfinLibraryWatcher.State.Syncing -> "Syncing…"
            is JellyfinLibraryWatcher.State.Synced -> "Up to date · %,d titles".format(titles)
            JellyfinLibraryWatcher.State.SignedOut -> "The sign-in has expired. Sign out and sign in again."
            is JellyfinLibraryWatcher.State.Failed -> "Couldn't sync: ${sync.message}"
        },
        color = if (sync is JellyfinLibraryWatcher.State.Failed || sync == JellyfinLibraryWatcher.State.SignedOut)
            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(ApertureTheme.spacing.large))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.small)) {
        OutlinedButton(onClick = viewModel::signOut) {
            Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Sign out")
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = viewModel::syncNow, enabled = mode.usesJellyfin && sync != JellyfinLibraryWatcher.State.Syncing) {
            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Sync now")
        }
        Button(onClick = onDone) { Text("Done") }
    }
}
