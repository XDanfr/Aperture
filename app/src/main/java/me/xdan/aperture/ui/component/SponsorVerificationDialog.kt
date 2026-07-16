package me.xdan.aperture.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import me.xdan.aperture.data.sponsor.SponsorVerificationState
import me.xdan.aperture.ui.screen.settings.GITHUB_DEVICE_QR_ROWS
import me.xdan.aperture.ui.screen.settings.ThemedQrCode

@Composable
fun SponsorVerificationDialog(
    state: SponsorVerificationState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.width(760.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(34.dp),
                horizontalArrangement = Arrangement.spacedBy(30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state is SponsorVerificationState.AwaitingAuthorization) {
                    ThemedQrCode(GITHUB_DEVICE_QR_ROWS)
                }
                Column(
                    modifier = Modifier.width(if (state is SponsorVerificationState.AwaitingAuthorization) 400.dp else 680.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Verify GitHub sponsorship", style = MaterialTheme.typography.headlineSmall)
                    when (state) {
                        SponsorVerificationState.Idle,
                        SponsorVerificationState.RequestingCode -> Text("Requesting a secure code from GitHub…")
                        is SponsorVerificationState.AwaitingAuthorization -> {
                            Text("Scan the QR code, sign in to GitHub, then enter this code:")
                            Text(state.userCode, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                            Text(state.verificationUri, color = MaterialTheme.colorScheme.primary)
                            Text("Aperture will continue automatically after approval.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                        }
                        SponsorVerificationState.CheckingSponsorship -> Text("Authorised. Checking your sponsorship…")
                        SponsorVerificationState.Verified -> Text("Thank you for sponsoring Aperture. You will not see sponsorship prompts again.")
                        SponsorVerificationState.NotSponsor -> Text("GitHub did not report an active sponsorship for XDanfr on this account.")
                        is SponsorVerificationState.Error -> Text(state.message)
                    }
                    Row(modifier = Modifier.align(Alignment.End)) {
                        if (state is SponsorVerificationState.Error || state == SponsorVerificationState.NotSponsor) {
                            OutlinedButton(onClick = onRetry) { Text("Try again") }
                            Spacer(Modifier.width(12.dp))
                        }
                        Button(onClick = onDismiss) { Text(if (state == SponsorVerificationState.Verified) "Done" else "Close") }
                    }
                }
            }
        }
    }
}
