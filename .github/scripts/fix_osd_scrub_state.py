from pathlib import Path
import re

vm = Path('app/src/main/java/me/xdan/aperture/ui/screen/player/PlayerViewModel.kt')
screen = Path('app/src/main/java/me/xdan/aperture/ui/screen/player/PlayerScreen.kt')

vm_text = vm.read_text(encoding='utf-8')

old_timer = '''    private fun resetOsdTimer() {
        osdTimerJob?.cancel()
        osdTimerJob = viewModelScope.launch { delay(3000); _isOsdVisible.value = false }
    }'''
new_timer = '''    private fun resetOsdTimer() {
        osdTimerJob?.cancel()
        if (!player.isPlaying) return

        osdTimerJob = viewModelScope.launch {
            delay(3000)
            if (player.isPlaying) {
                _isOsdVisible.value = false
            }
        }
    }'''
if vm_text.count(old_timer) != 1:
    raise SystemExit('Expected exactly one resetOsdTimer block')
vm_text = vm_text.replace(old_timer, new_timer, 1)

old_error = '''    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {'''
new_error = '''    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                if (_isOsdVisible.value) resetOsdTimer()
            } else {
                osdTimerJob?.cancel()
                osdTimerJob = null
            }
        }

        override fun onPlayerError(error: PlaybackException) {'''
if vm_text.count(old_error) != 1:
    raise SystemExit('Expected exactly one playerListener declaration')
vm_text = vm_text.replace(old_error, new_error, 1)
vm.write_text(vm_text, encoding='utf-8')

screen_text = screen.read_text(encoding='utf-8')

old_cancel = '''    fun cancelScrubbing() {
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
    }'''
new_cancel = '''    fun cancelScrubbing() {
        if (!scrubbing) return

        seekJob?.cancel()
        seekJob = null
        holdDirection = 0

        player.seekTo(originalPosition.coerceIn(0L, duration))

        scrubbing = false
        onScrubbingChanged(false)

        player.play()
    }'''
if screen_text.count(old_cancel) != 1:
    raise SystemExit('Expected exactly one cancelScrubbing block')
screen_text = screen_text.replace(old_cancel, new_cancel, 1)

old_center = '''                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER -> {
                                endHold()

                                if (scrubbing) {
                                    commitScrubbing()
                                } else {
                                    beginScrubbing()
                                }

                                true
                            }'''
new_center = '''                            KeyEvent.KEYCODE_DPAD_CENTER,
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
                            }'''
if screen_text.count(old_center) != 1:
    raise SystemExit('Expected exactly one centre key handler')
screen_text = screen_text.replace(old_center, new_center, 1)

old_up = '''                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (scrubbing) {
                                    cancelScrubbing()
                                    onScrubUp()
                                    true
                                } else {
                                    false
                                }
                            }'''
new_up = '''                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (scrubbing) {
                                    endHold()
                                    cancelScrubbing()
                                    onScrubUp()
                                    true
                                } else {
                                    false
                                }
                            }'''
if screen_text.count(old_up) != 1:
    raise SystemExit('Expected exactly one DPAD_UP handler')
screen_text = screen_text.replace(old_up, new_up, 1)

old_down = '''                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (scrubbing) {
                                    cancelScrubbing()
                                    onScrubToControls()
                                    true
                                } else {
                                    false
                                }
                            }'''
new_down = '''                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (scrubbing) {
                                    endHold()
                                    cancelScrubbing()
                                    onScrubToControls()
                                    true
                                } else {
                                    false
                                }
                            }'''
if screen_text.count(old_down) != 1:
    raise SystemExit('Expected exactly one DPAD_DOWN handler')
screen_text = screen_text.replace(old_down, new_down, 1)

old_back = '''                            KeyEvent.KEYCODE_BACK -> {
                                if (scrubbing) {
                                    cancelScrubbing()
                                    onScrubToControls()
                                    true
                                } else {
                                    false
                                }
                            }'''
new_back = '''                            KeyEvent.KEYCODE_BACK -> false'''
if screen_text.count(old_back) != 1:
    raise SystemExit('Expected exactly one BACK key handler')
screen_text = screen_text.replace(old_back, new_back, 1)

anchor = '''    fun beginHold(direction: Int) {'''
back_handler = '''    BackHandler(enabled = scrubbing) {
        endHold()
        cancelScrubbing()
        onScrubToControls()
    }

'''
if screen_text.count(anchor) != 1:
    raise SystemExit('Expected exactly one beginHold anchor')
screen_text = screen_text.replace(anchor, back_handler + anchor, 1)

screen.write_text(screen_text, encoding='utf-8')
