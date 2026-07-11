package me.xdan.aperture.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import me.xdan.aperture.ui.screen.actions.MediaActionState

@Composable
fun MediaContextMenu(
    state: MediaActionState,
    fromContinueWatching: Boolean,
    opensToRight: Boolean,
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
    onPlayFromBeginning: () -> Unit,
    onRemoveContinue: () -> Unit,
    onToggleList: () -> Unit,
    onToggleWatched: () -> Unit,
    onHide: () -> Unit,
    onRefreshAssets: () -> Unit
) {
    val media = state.media ?: return
    val firstRequester = remember { FocusRequester() }
    var waitingForOpeningKeyRelease by remember(media.id) { mutableStateOf(true) }
    LaunchedEffect(media.id) { delay(100); runCatching { firstRequester.requestFocus() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (!waitingForOpeningKeyRelease) return@onPreviewKeyEvent false
                    val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter
                    if (!isSelect) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) waitingForOpeningKeyRelease = false
                    true
                }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.58f)),
            contentAlignment = if (opensToRight) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Surface(
                modifier = Modifier.width(390.dp).padding(
                    start = if (opensToRight) 0.dp else 42.dp,
                    end = if (opensToRight) 42.dp else 0.dp
                ),
                shape = if (opensToRight) {
                    androidx.compose.foundation.shape.RoundedCornerShape(28.dp, 4.dp, 28.dp, 28.dp)
                } else {
                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp, 28.dp, 28.dp, 28.dp)
                },
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(media.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    if (fromContinueWatching) {
                        ContextAction("Remove from Continue Watching", Icons.Rounded.RemoveCircle, onRemoveContinue, Modifier.focusRequester(firstRequester))
                    } else {
                        ContextAction("Info", Icons.Rounded.Info, onInfo, Modifier.focusRequester(firstRequester))
                    }
                    if (state.progress != null) ContextAction("Play from beginning", Icons.Rounded.Replay, onPlayFromBeginning)
                    ContextAction(
                        if (media.isFavorite) "Remove from My List" else "Add to My List",
                        if (media.isFavorite) Icons.Rounded.PlaylistRemove else Icons.Rounded.PlaylistAdd,
                        onToggleList
                    )
                    ContextAction(
                        if (state.progress?.isCompleted == true) "Mark as unwatched" else "Mark as watched",
                        if (state.progress?.isCompleted == true) Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.CheckCircle,
                        onToggleWatched
                    )
                    ContextAction("Refresh assets", Icons.Rounded.ImageSearch, onRefreshAssets)
                    if (fromContinueWatching) ContextAction("Info", Icons.Rounded.Info, onInfo)
                    ContextAction("Hide", Icons.Rounded.VisibilityOff, onHide)
                }
            }
        }
    }
}

@Composable
private fun ContextAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(text, modifier = Modifier.fillMaxWidth())
    }
}
