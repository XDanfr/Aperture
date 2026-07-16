package me.xdan.aperture

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.launch
import me.xdan.aperture.data.sponsor.SponsorVerificationManager
import me.xdan.aperture.ui.navigation.Destination
import me.xdan.aperture.ui.navigation.NavGraph
import me.xdan.aperture.ui.component.SponsorPromptDialog
import me.xdan.aperture.ui.component.SponsorVerificationDialog
import me.xdan.aperture.ui.screen.ambient.AmbientMode
import me.xdan.aperture.data.update.UpdateCheckState
import me.xdan.aperture.ui.screen.launch.ApertureLaunchAnimation
import me.xdan.aperture.ui.screen.settings.GITHUB_SPONSORS_QR_ROWS
import me.xdan.aperture.ui.screen.settings.GITHUB_SPONSORS_URL
import me.xdan.aperture.ui.screen.settings.LinkQrDialog
import me.xdan.aperture.ui.screen.settings.UpdateDialog
import me.xdan.aperture.ui.theme.ApertureTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sponsorVerificationManager: SponsorVerificationManager
    
    private var lastInteractionTime by mutableLongStateOf(System.currentTimeMillis())
    private var isAmbientActive by mutableStateOf(false)
    private var isPlayerActive by mutableStateOf(false)
    private val ambientDismissKeys = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: me.xdan.aperture.ui.MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val themeId by mainViewModel.themeId.collectAsState()
            val dynamicAccentArgb by mainViewModel.dynamicAccentArgb.collectAsState()
            val updateState by mainViewModel.updateState.collectAsState()
            val onboardingCompleted by mainViewModel.isOnboardingCompleted.collectAsState()
            val tutorialRequired by mainViewModel.isTutorialRequired.collectAsState()
            val sponsorVerified by sponsorVerificationManager.isVerified.collectAsState()
            val sponsorVerificationState by sponsorVerificationManager.state.collectAsState()
            val scope = rememberCoroutineScope()
            var dismissedUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
            var launchFinished by rememberSaveable { mutableStateOf(false) }
            var sponsorPromptHandledThisLaunch by rememberSaveable { mutableStateOf(false) }
            var sponsorPromptDelayElapsed by remember { mutableStateOf(false) }
            var showSponsorPrompt by rememberSaveable { mutableStateOf(false) }
            var showSponsorQr by rememberSaveable { mutableStateOf(false) }
            var showSponsorVerification by rememberSaveable { mutableStateOf(false) }
            val offeredUpdate = when (val state = updateState) {
                is UpdateCheckState.Available -> state
                is UpdateCheckState.PermissionRequired -> state.update
                is UpdateCheckState.Downloading -> state.update
                else -> null
            }
            val updateDialogVisible = launchFinished &&
                offeredUpdate != null &&
                offeredUpdate.version != dismissedUpdateVersion
            val updateBlocksSponsorPrompt = when (updateState) {
                UpdateCheckState.Checking,
                is UpdateCheckState.PermissionRequired,
                is UpdateCheckState.Downloading,
                is UpdateCheckState.Installing -> true
                is UpdateCheckState.Available -> updateDialogVisible
                else -> false
            }

            LaunchedEffect(onboardingCompleted, tutorialRequired) {
                sponsorPromptDelayElapsed = false
                if (onboardingCompleted == true && !tutorialRequired) {
                    delay(SPONSOR_PROMPT_TEST_DELAY_MS)
                    sponsorPromptDelayElapsed = true
                }
            }

            val sponsorPromptEligible = launchFinished &&
                sponsorPromptDelayElapsed &&
                onboardingCompleted == true &&
                !tutorialRequired &&
                !isPlayerActive &&
                !isAmbientActive &&
                !sponsorVerified &&
                !updateBlocksSponsorPrompt

            LaunchedEffect(sponsorPromptEligible, sponsorPromptHandledThisLaunch) {
                if (sponsorPromptEligible && !sponsorPromptHandledThisLaunch) {
                    sponsorPromptHandledThisLaunch = true
                    showSponsorPrompt = true
                }
            }

            LaunchedEffect(updateDialogVisible) {
                if (updateDialogVisible) {
                    showSponsorPrompt = false
                    showSponsorQr = false
                    showSponsorVerification = false
                }
            }

            LaunchedEffect(sponsorVerified) {
                if (sponsorVerified) showSponsorPrompt = false
            }

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
                                        !showSponsorPrompt &&
                                        !showSponsorQr &&
                                        !showSponsorVerification &&
                                        System.currentTimeMillis() - lastInteractionTime > 5 * 60 * 1000
                                    ) {
                                        isAmbientActive = true
                                    }
                                }
                            }

                            if (isAmbientActive) {
                                BackHandler {
                                    lastInteractionTime = System.currentTimeMillis()
                                    isAmbientActive = false
                                }
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
                                    onNavigate = { destination ->
                                        lastInteractionTime = System.currentTimeMillis()
                                        if (destination is Destination.Player) {
                                            isPlayerActive = true
                                        }
                                        backstack.add(destination)
                                    }
                                )
                            }
                        }
                    }

                    if (updateDialogVisible && offeredUpdate != null) {
                        UpdateDialog(
                            state = updateState,
                            onInstall = mainViewModel::downloadAndInstallUpdate,
                            onDismiss = { dismissedUpdateVersion = offeredUpdate.version }
                        )
                    } else if (showSponsorPrompt) {
                        SponsorPromptDialog(
                            onSponsor = {
                                showSponsorPrompt = false
                                showSponsorQr = true
                            },
                            onVerify = {
                                showSponsorPrompt = false
                                showSponsorVerification = true
                                scope.launch { sponsorVerificationManager.verify() }
                            },
                            onDismiss = { showSponsorPrompt = false }
                        )
                    } else if (showSponsorVerification) {
                        SponsorVerificationDialog(
                            state = sponsorVerificationState,
                            onRetry = { scope.launch { sponsorVerificationManager.verify() } },
                            onDismiss = {
                                showSponsorVerification = false
                                sponsorVerificationManager.resetState()
                            }
                        )
                    } else if (showSponsorQr) {
                        LinkQrDialog(
                            title = "Sponsor XDanfr on GitHub",
                            description = "Scan with your phone to support Aperture's development through GitHub Sponsors.",
                            url = GITHUB_SPONSORS_URL,
                            qrRows = GITHUB_SPONSORS_QR_ROWS,
                            onDismiss = { showSponsorQr = false }
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
        if (
            keyCode != KeyEvent.KEYCODE_BACK &&
            (isAmbientActive || keyCode in ambientDismissKeys)
        ) {
            ambientDismissKeys += keyCode
            isAmbientActive = false
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        lastInteractionTime = System.currentTimeMillis()
        if (ambientDismissKeys.remove(keyCode)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

private const val SPONSOR_PROMPT_TEST_DELAY_MS = 2_500L
