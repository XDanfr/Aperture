package me.xdan.aperture.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import kotlinx.coroutines.launch
import me.xdan.aperture.data.jellyfin.JellyfinCatalog
import me.xdan.aperture.domain.model.LibraryMode
import me.xdan.aperture.domain.repository.ProfileRepository
import me.xdan.aperture.domain.sidebar.SidebarLayout
import me.xdan.aperture.domain.sidebar.SidebarRepository
import me.xdan.aperture.ui.navigation.sidebarCandidates
import me.xdan.aperture.ui.platform.Dialog
import me.xdan.aperture.ui.theme.ApertureTheme
import org.koin.compose.koinInject

/** Settings › Sidebar: the entries between Home and Settings. */
@Composable
fun SidebarDialog(onDismiss: () -> Unit) {
    val profiles: ProfileRepository = koinInject()
    val catalog: JellyfinCatalog = koinInject()
    val mode by profiles.activeLibraryMode.collectAsState(LibraryMode.LOCAL)
    val libraries by catalog.libraries.collectAsState(emptyList())
    val items = remember(mode, libraries) {
        sidebarCandidates(mode, libraries).map { ArrangeItem(it.key, it.label, it.icon) }
    }
    ArrangeDialog(
        title = "Sidebar",
        hint = "Select an entry to move it. Home and Settings stay first and last.",
        items = items,
        repository = koinInject<SidebarRepository>(),
        onDismiss = onDismiss
    )
}

/** Settings › Home rows: the rows on a Jellyfin profile's Home. */
@Composable
fun HomeRowsDialog(onDismiss: () -> Unit) {
    val catalog: JellyfinCatalog = koinInject()
    val libraries by catalog.libraries.collectAsState(emptyList())
    val items = remember(libraries) {
        me.xdan.aperture.ui.screen.jellyfin.JellyfinHomeViewModel.candidates(libraries).map { (key, label) ->
            ArrangeItem(
                key, label,
                when (key) {
                    me.xdan.aperture.ui.screen.jellyfin.JellyfinHomeViewModel.SPOTLIGHT -> Icons.Rounded.Star
                    me.xdan.aperture.ui.screen.jellyfin.JellyfinHomeViewModel.CONTINUE -> Icons.Rounded.History
                    me.xdan.aperture.ui.screen.jellyfin.JellyfinHomeViewModel.NEXT_UP -> Icons.Rounded.SkipNext
                    else -> Icons.Rounded.NewReleases
                }
            )
        }
    }
    ArrangeDialog(
        title = "Home rows",
        hint = "Select a row to move it. Empty rows don't show on Home.",
        items = items,
        repository = koinInject<me.xdan.aperture.domain.sidebar.HomeRowsRepository>(),
        onDismiss = onDismiss
    )
}

data class ArrangeItem(val key: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/**
 * Orders and hides a profile's list of things. Select an entry to pick it up, move it with Up and
 * Down, and select again to put it down. The eye shows or hides it. Saves as it goes.
 */
@Composable
fun ArrangeDialog(
    title: String,
    hint: String,
    items: List<ArrangeItem>,
    repository: me.xdan.aperture.domain.sidebar.ProfileLayoutRepository,
    onDismiss: () -> Unit
) {
    val saved by repository.layout.collectAsState(SidebarLayout())
    val scope = rememberCoroutineScope()
    val candidates = items
    val byKey = remember(candidates) { candidates.associateBy { it.key } }
    // Edited locally and saved on each change, so the sidebar behind updates as you go.
    var order by remember(saved, candidates) { mutableStateOf(saved.arrangeAll(candidates.map { it.key })) }
    var hidden by remember(saved) { mutableStateOf(saved.hidden) }
    var moving by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val first = remember { FocusRequester() }

    fun save() = scope.launch { repository.save(SidebarLayout(order, hidden)) }

    Dialog(onDismissRequest = { if (moving != null) moving = null else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val windowInfo = LocalWindowInfo.current
        LaunchedEffect(windowInfo.isWindowFocused) {
            if (!windowInfo.isWindowFocused) return@LaunchedEffect
            repeat(30) {
                withFrameNanos { }
                if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            }
        }
        Surface(
            modifier = Modifier.width(620.dp).heightIn(max = 680.dp),
            shape = ApertureTheme.shapes.dialog,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(ApertureTheme.spacing.extraLarge)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (moving != null) "Move with Up and Down, then select to put it down."
                    else hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(ApertureTheme.spacing.medium))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(order, key = { _, key -> key }) { index, key ->
                        val entry = byKey[key] ?: return@itemsIndexed
                        val isMoving = moving == key
                        val isHidden = key in hidden
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ListItem(
                                selected = isMoving,
                                onClick = {
                                    if (isMoving) {
                                        moving = null
                                        save()
                                    } else moving = key
                                },
                                leadingContent = { Icon(entry.icon, contentDescription = null) },
                                headlineContent = { Text(entry.label) },
                                supportingContent = when {
                                    isMoving -> { { Text("Moving") } }
                                    isHidden -> { { Text("Hidden") } }
                                    else -> null
                                },
                                trailingContent = if (isMoving) { { Icon(Icons.Rounded.DragHandle, contentDescription = null) } } else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                                    // While an entry is picked up, Up and Down move it rather than focus.
                                    .onPreviewKeyEvent { event ->
                                        if (!isMoving || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        val delta = when (event.key) {
                                            Key.DirectionUp -> -1
                                            Key.DirectionDown -> 1
                                            Key.DirectionLeft, Key.DirectionRight -> return@onPreviewKeyEvent true
                                            else -> return@onPreviewKeyEvent false
                                        }
                                        val target = index + delta
                                        if (target in order.indices) {
                                            order = order.toMutableList().apply { add(target, removeAt(index)) }
                                            scope.launch { listState.animateScrollToItem((target - 1).coerceAtLeast(0)) }
                                        }
                                        true
                                    }
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    hidden = if (isHidden) hidden - key else hidden + key
                                    save()
                                },
                                enabled = !isMoving
                            ) {
                                Icon(
                                    if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (isHidden) "Show ${entry.label}" else "Hide ${entry.label}"
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(ApertureTheme.spacing.medium))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = {
                        moving = null
                        scope.launch { repository.reset() }
                    }) { Text("Reset") }
                    Spacer(Modifier.width(ApertureTheme.spacing.small))
                    Button(onClick = {
                        if (moving != null) {
                            moving = null
                            save()
                        }
                        onDismiss()
                    }) { Text("Done") }
                }
            }
        }
    }
}
