package me.xdan.aperture.desktop

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import java.awt.Window
import javax.swing.UIManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun main() {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    application {
        val backDispatcher = remember { DesktopBackDispatcher() }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Aperture",
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
            onPreviewKeyEvent = {
                if (it.key == Key.Escape || it.key == Key.Back) {
                    if (it.type == KeyEventType.KeyUp) backDispatcher.onBack()
                    true
                } else false
            }
        ) {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB9A1FF), surface = Color(0xFF17151E), background = Color(0xFF100E16))) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DesktopAperture(window, backDispatcher)
                }
            }
        }
    }
}

private class DesktopBackDispatcher {
    var onBack: () -> Unit = {}
}

private enum class LibraryPage(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home), Movies("Movies", Icons.Default.Movie),
    Shows("Shows", Icons.Default.Tv), MyList("My List", Icons.Default.Favorite),
    Settings("Folders", Icons.Default.Folder)
}

@Composable
private fun DesktopAperture(window: Window, backDispatcher: DesktopBackDispatcher) {
    val scope = rememberCoroutineScope()
    val store = remember { DesktopLibraryStore() }
    var settings by remember { mutableStateOf(LibrarySettings()) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf(ScanResult(emptyList(), emptyList())) }
    var scanning by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf(LibraryPage.Home) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<DesktopMedia?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var expandedByButton by remember { mutableStateOf(false) }
    val expanded = hovered || expandedByButton
    val sidebarWidth by animateDpAsState(if (expanded) 220.dp else 80.dp)

    LaunchedEffect(Unit) {
        try {
            settings = withContext(Dispatchers.IO) { store.load() }
            loaded = true
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            error = "Could not load the desktop library: ${exception.localizedMessage}. Restart after fixing access to the settings file."
        }
    }
    LaunchedEffect(loaded, settings.folders, refresh) {
        if (!loaded) return@LaunchedEffect
        scanning = true
        try { scan = scanFolders(settings.folders) } finally { scanning = false }
    }
    fun save(next: LibrarySettings) {
        if (!loaded || saving) return
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(next) }
                settings = next
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                error = "Could not save the library: ${exception.localizedMessage}"
            } finally { saving = false }
        }
    }
    fun addFolder() {
        if (picking || saving || !loaded) return
        picking = true
        scope.launch {
            try {
                val folder = pickFolder(window)
                if (folder != null && folder !in settings.folders) save(settings.copy(folders = settings.folders + folder))
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                error = "Could not open that folder: ${exception.localizedMessage}"
            } finally { picking = false }
        }
    }
    fun play(media: DesktopMedia) {
        scope.launch {
            try { openMedia(media); selected = null }
            catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                error = "Could not play this video: ${exception.localizedMessage}"
            }
        }
    }
    fun goBack() {
        when {
            selected != null -> selected = null
            expandedByButton -> expandedByButton = false
            query.isNotEmpty() -> query = ""
            page != LibraryPage.Home -> page = LibraryPage.Home
        }
    }
    SideEffect { backDispatcher.onBack = ::goBack }
    DisposableEffect(backDispatcher) { onDispose { backDispatcher.onBack = {} } }
    val visibleMedia = remember(scan.media, page, settings.favorites, query) {
        scan.media.filter {
            (when (page) {
                LibraryPage.Movies -> !it.isEpisode
                LibraryPage.Shows -> it.isEpisode
                LibraryPage.MyList -> it.path in settings.favorites
                else -> true
            }) && (query.isBlank() || it.info.title.contains(query.trim(), ignoreCase = true))
        }
    }
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.width(sidebarWidth).fillMaxHeight().background(MaterialTheme.colorScheme.surface).hoverable(hoverSource).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = { expandedByButton = !expandedByButton }) {
                Icon(Icons.Default.Menu, "Expand or collapse sidebar")
            }
            if (expanded) Text("APERTURE", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
            LibraryPage.entries.forEach { destination ->
                NavigationRailItem(
                    selected = page == destination,
                    onClick = { page = destination; query = ""; expandedByButton = false },
                    icon = { Icon(destination.icon, destination.label) },
                    label = if (expanded) ({ Text(destination.label) }) else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page != LibraryPage.Home || query.isNotEmpty()) {
                    IconButton(onClick = ::goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
                Text(page.label, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { refresh++ }, enabled = loaded && !scanning) { Icon(Icons.Default.Refresh, "Rescan folders") }
                Button(onClick = ::addFolder, enabled = loaded && !saving && !picking) { Text("Add folder") }
            }
            if (scanning || !loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (scan.warnings.isNotEmpty()) {
                Text("Some folders could not be fully scanned. Check folder access or reconnect the drive.", color = MaterialTheme.colorScheme.error)
            }
            if (page == LibraryPage.Settings) {
                Text("Videos open in your system video player. Desktop playback progress and online metadata are not available yet.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(settings.folders, key = { it }) { folder ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(folder, modifier = Modifier.weight(1f))
                            TextButton(onClick = { save(settings.copy(folders = settings.folders - folder)) }, enabled = !saving && !picking) { Text("Remove folder") }
                        }
                    }
                    items(scan.warnings) { warning -> Text(warning, color = MaterialTheme.colorScheme.error) }
                }
            } else {
                OutlinedTextField(query, { query = it }, label = { Text("Search your library") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (visibleMedia.isEmpty() && !scanning && loaded) {
                    Text(if (settings.folders.isEmpty()) "Add a folder to browse your movies and shows." else "No matching videos found.")
                }
                LazyVerticalGrid(columns = GridCells.Adaptive(190.dp), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(6.dp)) {
                    items(visibleMedia, key = { it.path }) { media ->
                        Card(onClick = { selected = media }, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                                Icon(if (media.isEpisode) Icons.Default.Tv else Icons.Default.Movie, null, modifier = Modifier.size(56.dp))
                            }
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(media.info.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                Text(media.subtitle, style = MaterialTheme.typography.bodySmall)
                                if (media.path in settings.favorites) Text("In My List", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { media ->
        MediaDialog(media, media.path in settings.favorites, !saving,
            onClose = { selected = null }, onPlay = { play(media) },
            onToggleFavorite = {
                val favorites = if (media.path in settings.favorites) settings.favorites - media.path else settings.favorites + media.path
                save(settings.copy(favorites = favorites))
            })
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text("Aperture") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Close") } },
            modifier = Modifier.onPreviewKeyEvent {
                if (it.key == Key.Escape) { if (it.type == KeyEventType.KeyUp) error = null; true } else false
            })
    }
}

@Composable
private fun MediaDialog(media: DesktopMedia, favorite: Boolean, enabled: Boolean, onClose: () -> Unit, onPlay: () -> Unit, onToggleFavorite: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Desktop dialogs have their own window. A full-window scrim gives
        // consistent outside-click dismissal without clicking the library below.
        Box(Modifier.fillMaxSize().onPreviewKeyEvent {
            if (it.key == Key.Escape || it.key == Key.Back) {
                if (it.type == KeyEventType.KeyUp) onClose()
                true
            } else false
        }, contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose))
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(media.info.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                    }
                    Text(media.subtitle)
                    Text(media.path, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onPlay) { Text("Play in system player") }
                    OutlinedButton(onClick = onToggleFavorite, enabled = enabled) { Text(if (favorite) "Remove from My List" else "Add to My List") }
                }
            }
        }
    }
}
