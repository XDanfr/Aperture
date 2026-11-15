package me.xdan.aperture.desktop

import me.xdan.aperture.ui.platform.LocalToggleFullscreen
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.material3.PointerFocus
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.ImageLoader
import coil3.SingletonImageLoader
import kotlinx.coroutines.delay
import me.xdan.aperture.companion.CompanionHub
import me.xdan.aperture.di.desktopModule
import me.xdan.aperture.di.sharedModule
import me.xdan.aperture.platform.AppBuild
import me.xdan.aperture.platform.AppConfig
import me.xdan.aperture.platform.AppPlatform
import me.xdan.aperture.ui.ApertureApp
import me.xdan.aperture.ui.ApertureAppState
import me.xdan.aperture.ui.platform.DesktopBackDispatcher
import me.xdan.aperture.ui.platform.DesktopKeyRepeat
import me.xdan.aperture.ui.platform.LocalDesktopBackDispatcher
import me.xdan.aperture.ui.platform.isBackKey
import me.xdan.aperture.ui.screen.player.PlaybackPlatform
import me.xdan.aperture.ui.screen.player.PlayerEngine
import org.koin.core.context.startKoin
import java.awt.Dimension

/**
 * Aperture's layouts are designed for a 1080p TV, which Android presents as 960×540 dp. Scaling
 * density with the window width keeps every screen laid out exactly as on the TV, at any size.
 */
private const val TV_WIDTH_DP = 960f

fun main(args: Array<String>) {
    // `--check-libmpv [file]`: report whether libmpv loads, then exit. The packaging scripts run it
    // on each built app; the optional file catches the result where a GUI launcher has no console.
    val check = args.indexOf("--check-libmpv")
    if (check >= 0) {
        val result = me.xdan.aperture.desktop.mpv.MpvSelfCheck.run()
        println(result.message)
        args.getOrNull(check + 1)?.let { java.io.File(it).writeText(result.message + "\n") }
        kotlin.system.exitProcess(if (result.ok) 0 else 1)
    }
    DarkTitleBar.preferDarkWindowAppearance()
    AppBuild.install(
        AppConfig(
            platform = AppPlatform.Desktop,
            versionName = DesktopBuildConfig.VERSION_NAME,
            versionCode = 1,
            isDebug = System.getProperty("aperture.debug") == "true",
            tmdbApiKey = DesktopBuildConfig.TMDB_API_KEY,
            openSubtitlesApiKey = DesktopBuildConfig.OPENSUBTITLES_API_KEY,
            sideloadUpdatesEnabled = false,
            githubSponsorshipEnabled = true,
            sponsorPromptsEnabled = true,
        )
    )
    val koin = startKoin { modules(sharedModule, desktopModule) }.koin
    SingletonImageLoader.setSafe { koin.get<ImageLoader>() }
    val appState = ApertureAppState(koin.get())
    val companionHub = koin.get<CompanionHub>()
    val backDispatcher = DesktopBackDispatcher()

    application {
        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 720.dp),
            position = WindowPosition(Alignment.Center)
        )
        var fullscreen by remember { mutableStateOf(false) }
        LaunchedEffect(fullscreen) {
            windowState.placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
        }
        DisposableEffect(Unit) {
            companionHub.start()
            onDispose { companionHub.stop() }
        }
        val icon = remember { BitmapPainter(useResource("aperture-icon.png", ::loadImageBitmap)) }

        fun quit() {
            companionHub.stop()
            koin.get<PlayerEngine>().release()
            exitApplication()
        }

        Window(
            onCloseRequest = ::quit,
            state = windowState,
            title = "Aperture",
            icon = icon,
            onPreviewKeyEvent = { event ->
                handleWindowKey(event, appState, backDispatcher) { fullscreen = !fullscreen }
            }
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(640, 360)
                DarkTitleBar.useDarkTitleBar(window)
            }
            WindowFocus.manager = LocalFocusManager.current
            CompositionLocalProvider(
                LocalDesktopBackDispatcher provides backDispatcher,
                LocalToggleFullscreen provides { fullscreen = !fullscreen }
            ) {
                TvScaled(onPointerActivity = appState::onPointerActivity, onBack = backDispatcher::dispatch) {
                    ApertureApp(appState = appState, onExit = ::quit)
                    ToastHost(koin.get<PlaybackPlatform>() as DesktopPlaybackPlatform)
                }
            }
        }
    }
}

/**
 * Keys that move or use focus. When the mouse has parked focus, they first bring it back. (Not
 * Space: the player uses it for play/pause wherever focus is.)
 */
private val FocusKeys = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Tab,
)

private val ArrowDirections = mapOf(
    Key.DirectionUp to FocusDirection.Up,
    Key.DirectionDown to FocusDirection.Down,
    Key.DirectionLeft to FocusDirection.Left,
    Key.DirectionRight to FocusDirection.Right,
)

/** What the window-level key handling needs to know about focus. */
private object WindowFocus {
    var manager: FocusManager? = null
    /** Whether anything in the app has focus. */
    var appHasFocus = false
}

/**
 * Browser-style Back: Alt+Left everywhere, ⌘[ on macOS. Not while typing, where Alt/Option+Left
 * moves the cursor by word.
 */
private fun KeyEvent.isBackShortcut(): Boolean = !PointerFocus.isTextInputFocused &&
    ((key == Key.DirectionLeft && isAltPressed) || (key == Key.LeftBracket && isMetaPressed))

/** The release of a key whose press only restored focus, so it doesn't also click. */
private var swallowedKeyUp: Key? = null

/**
 * Window-wide keys, before any screen sees them: Escape, Alt+Left and ⌘[ are Back (released, as
 * Android dispatches it), F11 or Alt/⌘+Enter toggles fullscreen, keys that wake ambient mode are
 * swallowed, the first focus key after the mouse parked focus brings it back, and an arrow key
 * with nothing focused at all moves focus into the app, so the keyboard always does something.
 */
private fun handleWindowKey(
    event: KeyEvent,
    appState: ApertureAppState,
    backDispatcher: DesktopBackDispatcher,
    toggleFullscreen: () -> Unit
): Boolean {
    DesktopKeyRepeat.track(event)
    val keyCode = event.key.keyCode
    val isBack = event.isBackKey() || event.isBackShortcut()
    when (event.type) {
        KeyEventType.KeyDown -> {
            if (event.key == Key.F11 || (event.key == Key.Enter && (event.isAltPressed || event.isMetaPressed))) {
                toggleFullscreen()
                return true
            }
            if (appState.onKeyDown(keyCode, isBack)) return true
            if (isBack) return true
            if (event.key in FocusKeys && PointerFocus.restore()) {
                swallowedKeyUp = event.key
                return true
            }
            val direction = ArrowDirections[event.key]
            if (direction != null && !WindowFocus.appHasFocus &&
                WindowFocus.manager?.moveFocus(direction) == true
            ) {
                swallowedKeyUp = event.key
                return true
            }
        }
        KeyEventType.KeyUp -> {
            if (event.key == swallowedKeyUp) {
                swallowedKeyUp = null
                return true
            }
            if (appState.onKeyUp(keyCode)) return true
            if (isBack) {
                backDispatcher.dispatch()
                return true
            }
        }
    }
    return false
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvScaled(onPointerActivity: () -> Unit, onBack: () -> Unit, content: @Composable () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onFocusChanged {
                WindowFocus.appHasFocus = it.hasFocus
                if (it.hasFocus) PointerFocus.onFocusTaken()
            }
            .onPointerEvent(PointerEventType.Move) { onPointerActivity() }
            .onPointerEvent(PointerEventType.Press) { event ->
                onPointerActivity()
                if (event.buttons.isBackPressed) onBack()
            }
    ) {
        val scale = constraints.maxWidth / TV_WIDTH_DP
        CompositionLocalProvider(LocalDensity provides Density(density = scale, fontScale = 1f)) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

/** Android-toast-style notices from the player. */
@Composable
private fun ToastHost(platform: DesktopPlaybackPlatform) {
    val toast by platform.toast.collectAsState()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(toast) {
        if (toast == null) return@LaunchedEffect
        visible = true
        delay(2_000)
        visible = false
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.padding(bottom = 48.dp)) {
            Surface(
                shape = RoundedCornerShape(50),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.inverseSurface)
            ) {
                Text(
                    toast?.message.orEmpty(),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}
