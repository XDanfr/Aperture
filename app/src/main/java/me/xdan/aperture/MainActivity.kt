package me.xdan.aperture

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import me.xdan.aperture.ui.navigation.Destination
import me.xdan.aperture.ui.navigation.NavGraph
import me.xdan.aperture.ui.screen.ambient.AmbientMode
import me.xdan.aperture.data.update.UpdateCheckState
import me.xdan.aperture.ui.screen.launch.ApertureLaunchAnimation
import me.xdan.aperture.ui.screen.settings.UpdateDialog
import me.xdan.aperture.ui.theme.ApertureTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var lastInteractionTime by mutableLongStateOf(System.currentTimeMillis())
    private var isAmbientActive by mutableStateOf(false)
    private var isPlayerActive by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: me.xdan.aperture.ui.MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val themeId by mainViewModel.themeId.collectAsState()
            val dynamicAccentArgb by mainViewModel.dynamicAccentArgb.collectAsState()
            val updateState by mainViewModel.updateState.collectAsState()
            var dismissedUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
            var launchFinished by rememberSaveable { mutableStateOf(false) }
            ApertureTheme(
                themeId = themeId,
                dynamicAccent = dynamicAccentArgb?.let { androidx.compose.ui.graphics.Color(it) }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = launchFinished,
                        enter = fadeIn(tween(360)) + scaleIn(tween(420), initialScale = 0.985f)
                    ) {
                        androidx.tv.material3.Surface(
                            modifier = Modifier.fillMaxSize(),
                            colors = androidx.tv.material3.SurfaceDefaults.colors(
                                containerColor = androidx.tv.material3.MaterialTheme.colorScheme.background
                            )
                        ) {
                            @Suppress("UNCHECKED_CAST")
                            val backstack = rememberNavBackStack(Destination.Home) as NavBackStack<Destination>

                            LaunchedEffect(Unit) {
                                while (true) {
                                    delay(1000)
                                    if (!isPlayerActive &&
                                        System.currentTimeMillis() - lastInteractionTime > 5 * 60 * 1000
                                    ) {
                                        isAmbientActive = true
                                    }
                                }
                            }

                            if (isAmbientActive) {
                                AmbientMode()
                            } else {
                                NavGraph(
                                    backstack = backstack,
                                    mainViewModel = mainViewModel,
                                    onPlayerStateChanged = { isPlayerActive = it },
                                    onPreviewAmbientMode = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        isAmbientActive = true
                                    },
                                    onNavigate = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        backstack.add(it)
                                    }
                                )
                            }
                        }
                    }

                    val offeredUpdate = when (val state = updateState) {
                        is UpdateCheckState.Available -> state
                        is UpdateCheckState.PermissionRequired -> state.update
                        is UpdateCheckState.Downloading -> state.update
                        else -> null
                    }
                    if (
                        launchFinished &&
                        offeredUpdate != null &&
                        offeredUpdate.version != dismissedUpdateVersion
                    ) {
                        UpdateDialog(
                            state = updateState,
                            onInstall = mainViewModel::downloadAndInstallUpdate,
                            onDismiss = { dismissedUpdateVersion = offeredUpdate.version }
                        )
                    }

                    AnimatedVisibility(
                        visible = !launchFinished,
                        exit = fadeOut(tween(260))
                    ) {
                        ApertureLaunchAnimation(
                            onFinished = { launchFinished = true }
                        )
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        lastInteractionTime = System.currentTimeMillis()
        if (isAmbientActive) {
            isAmbientActive = false
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        lastInteractionTime = System.currentTimeMillis()
        return super.onKeyUp(keyCode, event)
    }
}
