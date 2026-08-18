from pathlib import Path

# Keep the scrub-state patcher idempotent so repeated workflow triggers are safe.
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

# Older workflow runs could leave the BackHandler duplicated. Collapse it to one.
double_handler = new_handler + new_handler
while double_handler in screen_text:
    screen_text = screen_text.replace(double_handler, new_handler, 1)

# Add a local visual scrub state to PlayerOsd so the non-timeline UI can disappear
# while the preview is active, without changing the existing scrub state itself.
old_state = """    var currentPosition by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration) }
    var isPlaying by remember { mutableStateOf(player.playWhenReady) }
"""
new_state = """    var currentPosition by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration) }
    var isPlaying by remember { mutableStateOf(player.playWhenReady) }
    var isScrubbing by remember { mutableStateOf(false) }
"""
if old_state in screen_text:
    screen_text = screen_text.replace(old_state, new_state, 1)
elif new_state not in screen_text:
    raise SystemExit("Could not find PlayerOsd state block")

# Normalize any duplicate declarations left by previous workflow runs.
scrub_state_line = "    var isScrubbing by remember { mutableStateOf(false) }\n"
while scrub_state_line + scrub_state_line in screen_text:
    screen_text = screen_text.replace(scrub_state_line + scrub_state_line, scrub_state_line)

old_callback = """                onScrubbingChanged = onScrubbingChanged,
"""
new_callback = """                onScrubbingChanged = { scrubbing ->
                    isScrubbing = scrubbing
                    onScrubbingChanged(scrubbing)
                },
"""
if old_callback in screen_text:
    screen_text = screen_text.replace(old_callback, new_callback, 1)
elif new_callback not in screen_text:
    raise SystemExit("Could not find PlayerOsd scrub callback")

old_title = """            Text(
                text = media?.title ?: "",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

"""
new_title = """            AnimatedVisibility(
                visible = !isScrubbing,
                enter = fadeIn(animationSpec = tween(220)) +
                    slideInVertically(animationSpec = tween(220), initialOffsetY = { it / 5 }) +
                    scaleIn(animationSpec = tween(220), initialScale = 0.96f),
                exit = fadeOut(animationSpec = tween(160)) +
                    slideOutVertically(animationSpec = tween(160), targetOffsetY = { -it / 5 }) +
                    scaleOut(animationSpec = tween(160), targetScale = 0.96f)
            ) {
                Column {
                    Text(
                        text = media?.title ?: "",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

"""
if old_title in screen_text:
    screen_text = screen_text.replace(old_title, new_title, 1)
elif new_title not in screen_text:
    raise SystemExit("Could not find PlayerOsd title block")

old_bottom = """            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(currentPosition), color = Color.White)
                Text(formatTime(duration), color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerControlIconButton(
                    icon = Icons.Rounded.Replay,
                    contentDescription = "Restart",
                    onClick = onRestart
                )
                Spacer(modifier = Modifier.width(24.dp))
                PlayerControlIconButton(
                    icon = Icons.Rounded.FastRewind,
                    contentDescription = "Rewind",
                    onClick = {
                        player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                        onInteraction()
                    }
                )
                Spacer(modifier = Modifier.width(32.dp))
                PlayerControlIconButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    iconSize = 64.dp,
                    onClick = {
                        if (isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                        onInteraction()
                    },
                    modifier = Modifier.focusRequester(controlsFocusRequester)
                )
                Spacer(modifier = Modifier.width(32.dp))
                PlayerControlIconButton(
                    icon = Icons.Rounded.FastForward,
                    contentDescription = "Fast Forward",
                    onClick = {
                        val safeDuration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        player.seekTo((player.currentPosition + 10000).coerceAtMost(safeDuration))
                        onInteraction()
                    }
                )
                Spacer(modifier = Modifier.width(32.dp))
                PlayerControlIconButton(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = "Audio and subtitle options",
                    onClick = onQuickMenu
                )
            }
"""
new_bottom = """            AnimatedVisibility(
                visible = !isScrubbing,
                enter = fadeIn(animationSpec = tween(220)) +
                    slideInVertically(animationSpec = tween(220), initialOffsetY = { it / 5 }) +
                    scaleIn(animationSpec = tween(220), initialScale = 0.96f),
                exit = fadeOut(animationSpec = tween(160)) +
                    slideOutVertically(animationSpec = tween(160), targetOffsetY = { it / 5 }) +
                    scaleOut(animationSpec = tween(160), targetScale = 0.96f)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color.White)
                        Text(formatTime(duration), color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerControlIconButton(
                            icon = Icons.Rounded.Replay,
                            contentDescription = "Restart",
                            onClick = onRestart
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        PlayerControlIconButton(
                            icon = Icons.Rounded.FastRewind,
                            contentDescription = "Rewind",
                            onClick = {
                                player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                                onInteraction()
                            }
                        )
                        Spacer(modifier = Modifier.width(32.dp))
                        PlayerControlIconButton(
                            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            iconSize = 64.dp,
                            onClick = {
                                if (isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                                onInteraction()
                            },
                            modifier = Modifier.focusRequester(controlsFocusRequester)
                        )
                        Spacer(modifier = Modifier.width(32.dp))
                        PlayerControlIconButton(
                            icon = Icons.Rounded.FastForward,
                            contentDescription = "Fast Forward",
                            onClick = {
                                val safeDuration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                player.seekTo((player.currentPosition + 10000).coerceAtMost(safeDuration))
                                onInteraction()
                            }
                        )
                        Spacer(modifier = Modifier.width(32.dp))
                        PlayerControlIconButton(
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = "Audio and subtitle options",
                            onClick = onQuickMenu
                        )
                    }
                }
            }
"""
if old_bottom in screen_text:
    screen_text = screen_text.replace(old_bottom, new_bottom, 1)
elif new_bottom not in screen_text:
    raise SystemExit("Could not find PlayerOsd bottom controls block")

# The visual patch uses tween(), which is not included by androidx.compose.animation.*.
screen_text = screen_text.replace(
    "animationSpec = tween(",
    "animationSpec = androidx.compose.animation.core.tween("
)

screen.write_text(screen_text, encoding="utf-8")
