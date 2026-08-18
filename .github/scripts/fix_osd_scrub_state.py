from pathlib import Path

vm = Path("app/src/main/java/me/xdan/aperture/ui/screen/player/PlayerViewModel.kt")
screen = Path("app/src/main/java/me/xdan/aperture/ui/screen/player/PlayerScreen.kt")

vm_text = vm.read_text(encoding="utf-8")

old_timer = """    private fun resetOsdTimer() {
        osdTimerJob?.cancel()
        osdTimerJob = viewModelScope.launch { delay(3000); _isOsdVisible.value = false }
    }"""
new_timer = """    private fun resetOsdTimer() {
        osdTimerJob?.cancel()
        if (!player.isPlaying) return

        osdTimerJob = viewModelScope.launch {
            delay(3000)
            if (player.isPlaying) {
                _isOsdVisible.value = false
            }
        }
    }"""
if old_timer in vm_text:
    vm_text = vm_text.replace(old_timer, new_timer, 1)
elif new_timer not in vm_text:
    raise SystemExit("Could not find resetOsdTimer in either old or already-patched form")

old_listener = """    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {"""
new_listener = """    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                if (_isOsdVisible.value) resetOsdTimer()
            } else {
                osdTimerJob?.cancel()
                osdTimerJob = null
            }
        }

        override fun onPlayerError(error: PlaybackException) {"""
if old_listener in vm_text:
    vm_text = vm_text.replace(old_listener, new_listener, 1)
elif new_listener not in vm_text:
    raise SystemExit("Could not find playerListener in either old or already-patched form")

vm.write_text(vm_text, encoding="utf-8")

screen_text = screen.read_text(encoding="utf-8")

old_cancel = """    fun cancelScrubbing() {
        if (!scrubbing) return

        seekJob?.cancel()
        seekJob = null
        holdDirection = 0

        player.seekTo(originalPosition.coerceIn(0L, duration))

        scrubbing = false
        onScrubbingChanged(false)

        if (wasPlayingBeforeScrub) {
            player.play()
        }
    }"""
new_cancel = """    fun cancelScrubbing() {
        if (!scrubbing) return

        seekJob?.cancel()
        seekJob = null
        holdDirection = 0

        player.seekTo(originalPosition.coerceIn(0L, duration))

        scrubbing = false
        onScrubbingChanged(false)

        player.play()
    }"""
if old_cancel in screen_text:
    screen_text = screen_text.replace(old_cancel, new_cancel, 1)
elif new_cancel not in screen_text:
    raise SystemExit("Could not find cancelScrubbing in either old or already-patched form")

old_center = """                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER -> {
                                endHold()

                                if (scrubbing) {
                                    commitScrubbing()
                                } else {
                                    beginScrubbing()
                                }

                                true
                            }"""
new_center = """                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER -> {
                                endHold()

                                if (scrubbing) {
                                    commitScrubbing()
                                } else if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }

                                true
                            }"""
if old_center in screen_text:
    screen_text = screen_text.replace(old_center, new_center, 1)
elif new_center not in screen_text:
    raise SystemExit("Could not find centre key handler in either old or already-patched form")

old_up = """                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (scrubbing) {
                                    cancelScrubbing()
                                    onScrubUp()
                                    true
                                } else {
                                    false
                                }
                            }"""
new_up = """                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (scrubbing) {
                                    endHold()
                                    cancelScrubbing()
                                    onScrubUp()
                                    true
                                } else {
                                    false
                                }
                            }"""
if old_up in screen_text:
    screen_text = screen_text.replace(old_up, new_up, 1)
elif new_up not in screen_text:
    raise SystemExit("Could not find DPAD_UP handler in either old or already-patched form")

old_down = """                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (scrubbing) {
                                    cancelScrubbing()
                                    onScrubToControls()
                                    true
                                } else {
                                    false
                                }
                            }"""
new_down = """                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (scrubbing) {
                                    endHold()
                                    cancelScrubbing()
                                    onScrubToControls()
                                    true
                                } else {
                                    false
                                }
                            }"""
if old_down in screen_text:
    screen_text = screen_text.replace(old_down, new_down, 1)
elif new_down not in screen_text:
    raise SystemExit("Could not find DPAD_DOWN handler in either old or already-patched form")

old_back = """                            KeyEvent.KEYCODE_BACK -> {
                                if (scrubbing) {
                                    cancelScrubbing()
                                    onScrubToControls()
                                    true
                                } else {
                                    false
                                }
                            }"""
new_back = """                            KeyEvent.KEYCODE_BACK -> false"""
if old_back in screen_text:
    screen_text = screen_text.replace(old_back, new_back, 1)
elif new_back not in screen_text:
    raise SystemExit("Could not find BACK key handler in either old or already-patched form")

old_handler = """    BackHandler(enabled = scrubbing) {
        endHold()
        cancelScrubbing()
        onScrubToControls()
    }

"""
new_handler = """    BackHandler(enabled = scrubbing) {
        cancelScrubbing()
        onScrubToControls()
    }

"""
if old_handler in screen_text:
    screen_text = screen_text.replace(old_handler, new_handler, 1)
elif new_handler not in screen_text:
    anchor = "    fun beginHold(direction: Int) {"
    if screen_text.count(anchor) != 1:
        raise SystemExit("Could not find unique beginHold anchor")
    screen_text = screen_text.replace(anchor, new_handler + anchor, 1)

# Scrub-only visual mode for the rest of the OSD.
imports_anchor = "import androidx.compose.ui.Modifier\n"
if "import androidx.compose.ui.draw.alpha\n" not in screen_text:
    screen_text = screen_text.replace(
        imports_anchor,
        imports_anchor + "import androidx.compose.ui.draw.alpha\nimport androidx.compose.ui.draw.scale\n",
        1,
    )

state_anchor = "    var isPlaying by remember { mutableStateOf(player.playWhenReady) }\n"
state_insert = """    var isPlaying by remember { mutableStateOf(player.playWhenReady) }
    var isScrubbing by remember { mutableStateOf(false) }
    val scrubUiAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isScrubbing) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "scrubUiAlpha"
    )
    val scrubUiOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isScrubbing) (-10).dp else 0.dp,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "scrubUiOffset"
    )
    val scrubUiScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isScrubbing) 0.98f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "scrubUiScale"
    )
    val scrubUiModifier = Modifier
        .alpha(scrubUiAlpha)
        .offset(y = scrubUiOffset)
        .scale(scrubUiScale)
"""
if state_anchor in screen_text and "val scrubUiModifier = Modifier" not in screen_text:
    screen_text = screen_text.replace(state_anchor, state_insert, 1)

callback_old = """                onScrubbingChanged = onScrubbingChanged,"""
callback_new = """                onScrubbingChanged = { scrubbing ->
                    isScrubbing = scrubbing
                    onScrubbingChanged(scrubbing)
                },"""
if callback_old in screen_text:
    screen_text = screen_text.replace(callback_old, callback_new, 1)

# Add the animated modifier to the title, time labels, and transport row.
title_old = """            Text(
                text = media?.title ?: \"\",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )"""
title_new = """            Text(
                text = media?.title ?: \"\",
                modifier = scrubUiModifier,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )"""
if title_old in screen_text:
    screen_text = screen_text.replace(title_old, title_new, 1)

# The first progress/time Row after PlayerSeekProgress is the time display.
time_old = """            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {"""
time_new = """            Row(
                modifier = scrubUiModifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {"""
if time_old in screen_text:
    screen_text = screen_text.replace(time_old, time_new, 1)

# The final centred control row immediately before PlayerOsd closes.
control_old = """            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerControlIconButton(
                    icon = Icons.Rounded.Replay,"""
control_new = """            Row(
                modifier = scrubUiModifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerControlIconButton(
                    icon = Icons.Rounded.Replay,"""
if control_old in screen_text:
    screen_text = screen_text.replace(control_old, control_new, 1)

screen.write_text(screen_text, encoding="utf-8")
