package me.xdan.aperture.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import me.xdan.aperture.companion.CompanionHub
import me.xdan.aperture.data.sponsor.SponsorVerificationManager
import me.xdan.aperture.data.update.UpdateCheckState
import me.xdan.aperture.platform.AppBuild
import me.xdan.aperture.platform.KeyValueStores
import me.xdan.aperture.ui.component.SponsorPromptDialog
import me.xdan.aperture.ui.component.SponsorVerificationDialog
import me.xdan.aperture.ui.navigation.Destination
import me.xdan.aperture.ui.navigation.NavGraph
import me.xdan.aperture.ui.platform.BackHandler
import me.xdan.aperture.ui.screen.ambient.AmbientMode
import me.xdan.aperture.ui.screen.launch.ApertureLaunchAnimation
import me.xdan.aperture.ui.screen.settings.GITHUB_SPONSORS_QR_ROWS
import me.xdan.aperture.ui.screen.settings.GITHUB_SPONSORS_URL
import me.xdan.aperture.ui.screen.settings.LinkQrDialog
import me.xdan.aperture.ui.screen.settings.UpdateDialog
import me.xdan.aperture.ui.theme.ApertureTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Window-level state the platform entry point feeds: idle time for ambient mode, whether the
 * player is up, and keys swallowed while dismissing ambient mode.
 */
class ApertureAppState(stores: KeyValueStores) {
    var lastInteractionTime by mutableLongStateOf(System.currentTimeMillis())
    var isAmbientActive by mutableStateOf(false)
    var isPlayerActive by mutableStateOf(false)
    private val ambientDismissKeys = mutableSetOf<Long>()

    /** This launch's number, for the sponsor prompt interval. */
    val launchNumber: Int

    init {
        val launchPreferences = stores.open("sponsor_prompt")
        launchNumber = launchPreferences.getInt(KEY_LAUNCH_COUNT, 0) + 1
        launchPreferences.edit().putInt(KEY_LAUNCH_COUNT, launchNumber).apply()

        // What's new version check, kinda does nothing FOR NOW
        val appVersionPreferences = stores.open(APP_VERSION_PREFERENCES)
        if (!appVersionPreferences.contains(KEY_LAST_SEEN_VERSION_CODE)) {
            appVersionPreferences.edit()
                .putInt(KEY_LAST_SEEN_VERSION_CODE, AppBuild.VERSION_CODE)
                .apply()
        }
    }

    /** Returns true when the key was consumed to leave ambient mode. */
    fun onKeyDown(key: Long, isBack: Boolean): Boolean {
        lastInteractionTime = System.currentTimeMillis()
        if (!isBack && (isAmbientActive || key in ambientDismissKeys)) {
            ambientDismissKeys += key
            isAmbientActive = false
            return true
        }
        return false
    }

    fun onKeyUp(key: Long): Boolean {
        lastInteractionTime = System.currentTimeMillis()
        return ambientDismissKeys.remove(key)
    }

    /** Pointer input counts as interaction too (desktop mice, Android touch). */
    fun onPointerActivity() {
        lastInteractionTime = System.currentTimeMillis()
    }
}

/** The whole app, as both the Android TV activity and the desktop window show it. */
@Composable
fun ApertureApp(appState: ApertureAppState, onExit: () -> Unit) {
    val sponsorVerificationManager: SponsorVerificationManager = koinInject()
    val companionHub: CompanionHub = koinInject()
    val mainViewModel: me.xdan.aperture.ui.MainViewModel = koinViewModel()
    val themeId by mainViewModel.themeId.collectAsState()
    val dynamicAccentArgb by mainViewModel.dynamicAccentArgb.collectAsState()
    val updateState by mainViewModel.updateState.collectAsState()
    val onboardingCompleted by mainViewModel.isOnboardingCompleted.collectAsState()
    val tutorialRequired by mainViewModel.isTutorialRequired.collectAsState()
    val legacyCleanupRequired by mainViewModel.legacyCleanupRequired.collectAsState()
    val profileRepository: me.xdan.aperture.domain.repository.ProfileRepository = koinInject()
    val profilesViewModel: me.xdan.aperture.ui.screen.profiles.ProfilesViewModel = koinViewModel()
    val profiles by profilesViewModel.profiles.collectAsState()
    val activeProfileId by profilesViewModel.activeProfileId.collectAsState()
    val profileChosen by profileRepository.profileChosen.collectAsState()
    LaunchedEffect(Unit) { profileRepository.resolveLaunchChoice() }
    // Keeps the active profile's Jellyfin index current while the app is open.
    val jellyfinWatcher: me.xdan.aperture.data.jellyfin.JellyfinLibraryWatcher = koinInject()
    LaunchedEffect(jellyfinWatcher) { jellyfinWatcher.run() }
    val pickerRequested by profileRepository.pickerRequested.collectAsState()
    // At launch only with several profiles; from the sidebar always, so a first extra profile can be added.
    val choosingProfile = onboardingCompleted == true && !profileChosen &&
        (profiles.size > 1 || pickerRequested) && profiles.isNotEmpty()
    // Read from long-lived effects (the companion collector), which would otherwise keep the first value.
    val choosingProfileNow by rememberUpdatedState(choosingProfile)
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
    val updateDialogVisible = AppBuild.SIDELOAD_UPDATES_ENABLED && launchFinished && !legacyCleanupRequired &&
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
        AppBuild.SPONSOR_PROMPTS_ENABLED &&
        sponsorPromptDelayElapsed &&
        onboardingCompleted == true &&
        !legacyCleanupRequired &&
        !tutorialRequired &&
        !choosingProfile &&
        !appState.isPlayerActive &&
        !appState.isAmbientActive &&
        !sponsorVerified &&
        appState.launchNumber % SPONSOR_PROMPT_LAUNCH_INTERVAL == 0 &&
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

    val themePhone by companionHub.themePhone.collectAsState()
    ApertureTheme(
        themeId = themeId,
        dynamicAccent = dynamicAccentArgb?.let { androidx.compose.ui.graphics.Color(it) },
        phoneColors = themePhone?.colors
    ) {
        val brandMotion = ApertureTheme.motion
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = launchFinished,
                enter = fadeIn(brandMotion.brandEffects()) + scaleIn(brandMotion.brandSpatial(), initialScale = 0.985f)
            ) {
                androidx.tv.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    colors = androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = androidx.tv.material3.MaterialTheme.colorScheme.background
                    )
                ) {
                    val backstack = me.xdan.aperture.ui.navigation.rememberDestinationBackStack()

                    // paired phones can start playback and leave the player.
                    LaunchedEffect(backstack) {
                        companionHub.navigation.collect { request ->
                            appState.lastInteractionTime = System.currentTimeMillis()
                            when (request) {
                                is me.xdan.aperture.companion.CompanionNavigation.Play -> {
                                    if (onboardingCompleted != true || legacyCleanupRequired || choosingProfileNow) return@collect
                                    appState.isAmbientActive = false
                                    if (backstack.lastOrNull() is Destination.Player) backstack.removeAt(backstack.lastIndex)
                                    appState.isPlayerActive = true
                                    backstack.add(Destination.Player(me.xdan.aperture.domain.model.MediaKey.Local(request.mediaId), request.fromStart))
                                }
                                me.xdan.aperture.companion.CompanionNavigation.ExitPlayer -> {
                                    if (backstack.size > 1 && backstack.lastOrNull() is Destination.Player) {
                                        backstack.removeAt(backstack.lastIndex)
                                    }
                                }
                            }
                        }
                    }

                    // A different profile has its own lists and progress, so start again from Home.
                    LaunchedEffect(backstack) {
                        snapshotFlow { activeProfileId }
                            .drop(1)
                            .collect {
                                if (backstack.size > 1 || backstack.lastOrNull() !is Destination.Home) {
                                    backstack.clear()
                                    backstack.add(Destination.Home)
                                }
                            }
                    }

                    LaunchedEffect(legacyCleanupRequired, onboardingCompleted) {
                        while (true) {
                            delay(1000)
                            if (!legacyCleanupRequired &&
                                onboardingCompleted == true &&
                                !appState.isPlayerActive &&
                                !choosingProfileNow &&
                                !showSponsorPrompt &&
                                !showSponsorQr &&
                                !showSponsorVerification &&
                                System.currentTimeMillis() - appState.lastInteractionTime > 5 * 60 * 1000
                            ) {
                                appState.isAmbientActive = true
                            }
                        }
                    }

                    if (legacyCleanupRequired) {
                        me.xdan.aperture.ui.screen.onboarding.LegacyInstallCleanup(
                            onRecheck = mainViewModel::refreshLegacyInstall,
                            onCancel = onExit
                        )
                    } else if (choosingProfile) {
                        me.xdan.aperture.ui.screen.profiles.ProfilePickerScreen(
                            profiles = profiles,
                            lastProfileId = activeProfileId,
                            onSelect = profilesViewModel::select,
                            onCreate = { name, color -> profilesViewModel.create(name, color) },
                            onUpdate = profilesViewModel::update,
                            onDelete = profilesViewModel::delete,
                            onDismiss = if (pickerRequested) profileRepository::dismissPicker else null
                        )
                    } else if (appState.isAmbientActive) {
                        BackHandler {
                            appState.lastInteractionTime = System.currentTimeMillis()
                            appState.isAmbientActive = false
                        }
                        AmbientMode()
                    } else {
                        NavGraph(
                            backstack = backstack,
                            mainViewModel = mainViewModel,
                            onPlayerStateChanged = {
                                appState.isPlayerActive = it
                                companionHub.onPlayerVisible(it)
                            },
                            onPreviewAmbientMode = {
                                appState.lastInteractionTime = System.currentTimeMillis()
                                appState.isAmbientActive = true
                            },
                            onNavigate = { destination ->
                                appState.lastInteractionTime = System.currentTimeMillis()
                                if (destination is Destination.Player) {
                                    appState.isPlayerActive = true
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
            } else if (AppBuild.GITHUB_SPONSORSHIP_ENABLED && showSponsorPrompt) {
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
            } else if (AppBuild.GITHUB_SPONSORSHIP_ENABLED && showSponsorVerification) {
                SponsorVerificationDialog(
                    state = sponsorVerificationState,
                    onRetry = { scope.launch { sponsorVerificationManager.verify() } },
                    onDismiss = {
                        showSponsorVerification = false
                        sponsorVerificationManager.resetState()
                    }
                )
            } else if (AppBuild.GITHUB_SPONSORSHIP_ENABLED && showSponsorQr) {
                LinkQrDialog(
                    title = "Sponsor XDanfr on GitHub",
                    description = "Scan with your phone to support Aperture's development through GitHub Sponsors.",
                    url = GITHUB_SPONSORS_URL,
                    qrRows = GITHUB_SPONSORS_QR_ROWS,
                    onDismiss = { showSponsorQr = false },
                    nextLabel = "Next",
                    onNext = {
                        showSponsorQr = false
                        showSponsorVerification = true
                        scope.launch { sponsorVerificationManager.verify() }
                    }
                )
            }

            if (launchFinished) {
                me.xdan.aperture.ui.screen.companion.CompanionPairingRequestHost()
            }

            AnimatedVisibility(
                visible = !launchFinished,
                exit = fadeOut(brandMotion.brandFastEffects())
            ) {
                ApertureLaunchAnimation(
                    onFinished = { launchFinished = true }
                )
            }
        }
    }
}

private const val SPONSOR_PROMPT_TEST_DELAY_MS = 2_500L
private const val SPONSOR_PROMPT_LAUNCH_INTERVAL = 10
private const val KEY_LAUNCH_COUNT = "launch_count"
private const val APP_VERSION_PREFERENCES = "app_version"
private const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"
