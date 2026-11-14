package me.xdan.aperture.ui.screen.companion

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import me.xdan.aperture.companion.CompanionHub
import me.xdan.aperture.companion.CompanionServerStatus
import me.xdan.aperture.companion.PairedPhone
import me.xdan.aperture.companion.PairingSession
import me.xdan.aperture.ui.screen.settings.ThemedQrCode
import me.xdan.aperture.ui.theme.ApertureTheme
import javax.inject.Inject

@HiltViewModel
class CompanionViewModel @Inject constructor(val hub: CompanionHub) : ViewModel()

/** rows of '1'/'0' modules for [ThemedQrCode], which draws its own quiet zone. */
internal fun qrRows(content: String): List<String> {
    val matrix = Encoder.encode(content, ErrorCorrectionLevel.M).matrix
    return List(matrix.height) { y ->
        buildString(matrix.width) { for (x in 0 until matrix.width) append(if (matrix.get(x, y).toInt() == 1) '1' else '0') }
    }
}

private fun String.spacedCode() = if (length == 6) substring(0, 3) + " " + substring(3) else this

/** requests Android 17's local network permission, then (re)starts the companion server. */
@Composable
private fun rememberLocalNetworkRequest(hub: CompanionHub): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hub.start()
    }
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= 37) launcher.launch(CompanionHub.LOCAL_NETWORK_PERMISSION) else hub.start()
        }
    }
}

/**
 * pairing from the TV's own menus (onboarding and Settings). opens a pairing
 * window while visible and lists phones that are already paired.
 */
@Composable
fun CompanionPairingDialog(
    onDismiss: () -> Unit,
    viewModel: CompanionViewModel = hiltViewModel()
) {
    val hub = viewModel.hub
    val status by hub.status.collectAsState()
    val session by hub.pairing.collectAsState()
    val phones by hub.pairedPhones.collectAsState()
    val requestPermission = rememberLocalNetworkRequest(hub)
    val doneRequester = remember { FocusRequester() }

    DisposableEffect(Unit) {
        hub.start()
        hub.refreshAddress()
        hub.openPairing()
        onDispose { hub.closePairing() }
    }
    // after a phone pairs, offer a fresh code so another phone can join.
    LaunchedEffect(session?.pairedDeviceName) {
        if (session?.pairedDeviceName != null) {
            delay(2_500)
            hub.closePairing()
            hub.openPairing()
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { doneRequester.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.width(860.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(34.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PairingCodePanel(session, status, hub)
                Column(
                    modifier = Modifier.weight(1f).heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Pair a phone", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Use Aperture Companion to browse your library, start playback and control the TV from your phone. " +
                            "Scan the code, or pick this TV in the app and type the code shown here.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                    )
                    when (status) {
                        CompanionServerStatus.PermissionRequired -> {
                            Text(
                                "Aperture needs permission to talk to devices on your local network.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(onClick = requestPermission) { Text("Allow local network access") }
                        }
                        CompanionServerStatus.Stopped -> Text(
                            "Connect this TV to Wi-Fi or Ethernet to pair a phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        is CompanionServerStatus.Running -> Unit
                    }
                    if (phones.isNotEmpty()) {
                        Text(
                            "Paired phones",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        phones.forEach { phone -> PairedPhoneRow(phone, onRemove = { hub.unpair(phone.deviceId) }) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        Button(onClick = onDismiss, modifier = Modifier.focusRequester(doneRequester)) {
                            Text(if (phones.isEmpty()) "Not now" else "Done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PairingCodePanel(session: PairingSession?, status: CompanionServerStatus, hub: CompanionHub) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val uri = session?.let(hub::pairingUri)
        val paired = session?.pairedDeviceName
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            when {
                paired != null -> PairedBadge(paired)
                uri != null -> ThemedQrCode(rows = remember(uri) { qrRows(uri) })
                else -> Surface(
                    modifier = Modifier.size(260.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(72.dp))
                    }
                }
            }
        }
        if (session != null && status is CompanionServerStatus.Running && paired == null) {
            Text(
                session.code.spacedCode(),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "${status.host ?: "No network"} · port ${status.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
            )
        }
    }
}

@Composable
private fun PairedBadge(name: String) {
    Surface(
        modifier = Modifier.size(260.dp),
        shape = RoundedCornerShape(24.dp),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "$name is paired",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun PairedPhoneRow(phone: PairedPhone, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.PhoneAndroid, contentDescription = null)
        Spacer(Modifier.width(ApertureTheme.spacing.small))
        Text(phone.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onRemove) { Text("Remove") }
    }
}

/**
 * shown over anything else when a phone picks this TV from its discovery list,
 * so the user can read the code without navigating to Settings.
 */
@Composable
fun CompanionPairingRequestHost(viewModel: CompanionViewModel = hiltViewModel()) {
    val hub = viewModel.hub
    val session by hub.pairing.collectAsState()
    val current = session ?: return
    val requester = current.requestedBy ?: return
    val okRequester = remember { FocusRequester() }

    LaunchedEffect(current.pairedDeviceName) {
        if (current.pairedDeviceName != null) {
            delay(2_000)
            hub.closePairing()
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { okRequester.requestFocus() }
    }

    Dialog(onDismissRequest = hub::closePairing) {
        Surface(
            modifier = Modifier.width(520.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier.padding(34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val paired = current.pairedDeviceName
                if (paired != null) {
                    PairedBadge(paired)
                } else {
                    Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text("Pair $requester?", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Enter this code on your phone",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                    )
                    Text(
                        current.code.spacedCode(),
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 8.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(onClick = hub::closePairing, modifier = Modifier.focusRequester(okRequester)) {
                    Text(if (paired != null) "Done" else "Cancel")
                }
            }
        }
    }
}
