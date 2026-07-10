package me.xdan.aperture.ui.screen.onboarding

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.*
import me.xdan.aperture.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val permissionState = rememberPermissionState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )

    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) {
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main Center UI
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.width(500.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_banner_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(300.dp),
                        tint = Color.Unspecified
                    )
                    
                    Text(
                        text = "We need access to your local storage to find and play your movie collection.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Button(
                        onClick = { permissionState.launchPermissionRequest() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}
