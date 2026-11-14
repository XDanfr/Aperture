package me.xdan.aperture.mobile.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import me.xdan.aperture.companion.protocol.CompanionProtocol
import me.xdan.aperture.mobile.data.DiscoveredTv
import me.xdan.aperture.mobile.data.PairingResult
import me.xdan.aperture.mobile.ui.CompanionViewModel

private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

private sealed interface PairStep {
    data object Choose : PairStep
    data object Discover : PairStep
    data class EnterCode(val tv: DiscoveredTv) : PairStep
    data object Manual : PairStep
}

/** pairing with a TV by QR code, network search, or typing the address and code. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PairingFlow(
    viewModel: CompanionViewModel,
    onPaired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<PairStep>(PairStep.Choose) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingAction?.invoke()
        else error = "Aperture Companion needs Nearby devices access to reach your TV on the local network."
        pendingAction = null
    }
    /** Android 17 gates all LAN traffic behind ACCESS_LOCAL_NETWORK. */
    fun withLocalNetwork(action: () -> Unit) {
        error = null
        if (Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(context, LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
        ) action() else {
            pendingAction = action
            permissionLauncher.launch(LOCAL_NETWORK)
        }
    }

    fun handle(result: PairingResult) {
        busy = false
        when (result) {
            PairingResult.Success -> onPaired()
            is PairingResult.Failure -> error = result.message
        }
    }

    fun scanQr() = withLocalNetwork {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val invite = barcode.rawValue?.let(viewModel::parseInvite)
                if (invite == null) {
                    error = "That isn't an Aperture pairing code. Open Settings › Paired phones on your TV."
                } else {
                    busy = true
                    scope.launch { handle(viewModel.pairWithInvite(invite)) }
                }
            }
            .addOnFailureListener {
                error = "The scanner couldn't start. Try finding your TV on the network instead."
            }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AnimatedContent(targetState = step, label = "pairStep") { current ->
            when (current) {
                PairStep.Choose -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "On your TV, open Aperture and go to Settings › Paired phones (or Pair a phone during setup).",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = ::scanQr,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(ButtonDefaults.MediumIconSize))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan the code on your TV", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                    }
                    FilledTonalButton(
                        onClick = { withLocalNetwork { step = PairStep.Discover } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight)
                    ) {
                        Icon(Icons.Rounded.WifiFind, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Find TVs on this network")
                    }
                    TextButton(onClick = { withLocalNetwork { step = PairStep.Manual } }, enabled = !busy) {
                        Icon(Icons.Rounded.Keyboard, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Enter the TV's address")
                    }
                }

                PairStep.Discover -> DiscoverStep(
                    viewModel = viewModel,
                    busy = busy,
                    onBack = { step = PairStep.Choose },
                    onPick = { tv ->
                        busy = true
                        error = null
                        scope.launch {
                            when (val result = viewModel.requestPairing(tv)) {
                                PairingResult.Success -> { busy = false; step = PairStep.EnterCode(tv) }
                                is PairingResult.Failure -> { busy = false; error = result.message }
                            }
                        }
                    }
                )

                is PairStep.EnterCode -> CodeStep(
                    title = "Enter the code shown on ${current.tv.name}",
                    busy = busy,
                    onBack = { step = PairStep.Discover },
                    onSubmit = { code ->
                        busy = true
                        error = null
                        scope.launch {
                            handle(viewModel.pairWithCode(current.tv.host, current.tv.port, code, current.tv.tvId, current.tv.name))
                        }
                    }
                )

                PairStep.Manual -> ManualStep(
                    busy = busy,
                    onBack = { step = PairStep.Choose },
                    onSubmit = { host, port, code ->
                        busy = true
                        error = null
                        scope.launch { handle(viewModel.pairWithCode(host, port, code)) }
                    }
                )
            }
        }
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Text("Connecting to your TV…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        error?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiscoverStep(
    viewModel: CompanionViewModel,
    busy: Boolean,
    onBack: () -> Unit,
    onPick: (DiscoveredTv) -> Unit
) {
    val flow = remember { runCatching { viewModel.discoverTvs() }.getOrDefault(emptyFlow()) }
    val tvs by flow.collectAsState(initial = emptyList())
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StepHeader("TVs on this network", onBack)
        if (tvs.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                LoadingIndicator(Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Looking for Aperture… Make sure it's open on your TV and both devices share a network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        tvs.forEach { tv ->
            Surface(
                onClick = { onPick(tv) },
                enabled = !busy,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    leadingContent = { Icon(Icons.Rounded.Tv, contentDescription = null) },
                    headlineContent = { Text(tv.name) },
                    supportingContent = { Text(tv.host) }
                )
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CodeStep(title: String, busy: Boolean, onBack: () -> Unit, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepHeader(title, onBack)
        OutlinedTextField(
            value = code,
            onValueChange = { value -> code = value.filter(Char::isDigit).take(CompanionProtocol.CODE_LENGTH) },
            label = { Text("6-digit code") },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        LaunchedEffect(code) {
            if (code.length == CompanionProtocol.CODE_LENGTH && !busy) onSubmit(code)
        }
        Button(
            onClick = { onSubmit(code) },
            enabled = !busy && code.length == CompanionProtocol.CODE_LENGTH,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Pair") }
    }
}

@Composable
private fun ManualStep(busy: Boolean, onBack: () -> Unit, onSubmit: (String, Int, String) -> Unit) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(CompanionProtocol.DEFAULT_PORT.toString()) }
    var code by remember { mutableStateOf("") }
    val valid = host.isNotBlank() && port.toIntOrNull() in 1..65_535 && code.length == CompanionProtocol.CODE_LENGTH
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepHeader("Enter the TV's address", onBack)
        Text(
            "Settings › Paired phones on the TV shows its address under the code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = host, onValueChange = { host = it.trim() }, label = { Text("IP address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = code, onValueChange = { code = it.filter(Char::isDigit).take(CompanionProtocol.CODE_LENGTH) },
            label = { Text("6-digit code") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { onSubmit(host, port.toInt(), code) }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Pair")
        }
    }
}
