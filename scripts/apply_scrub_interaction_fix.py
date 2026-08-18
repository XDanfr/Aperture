from pathlib import Path

screen = Path('app/src/main/java/me/xdan/aperture/ui/screen/player/PlayerScreen.kt')
text = screen.read_text(encoding='utf-8')

replacements = [
    (
        '''            PlayerSeekProgress(\n                player = player,\n                mediaSource = mediaSource,\n                progress = progress,\n                isPlaying = isPlaying,\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .height(24.dp)\n            )''',
        '''            PlayerSeekProgress(\n                player = player,\n                mediaSource = mediaSource,\n                progress = progress,\n                isPlaying = isPlaying,\n                onScrubbingChanged = viewModel::setScrubbing,\n                onScrubUp = viewModel::hideOsd,\n                onScrubToControls = controlsFocusRequester::requestFocus,\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .height(24.dp)\n            )'''
    ),
    (
        '''private fun PlayerSeekProgress(\n    player: androidx.media3.common.Player,\n    mediaSource: String?,\n    progress: Float,\n    isPlaying: Boolean,\n    modifier: Modifier = Modifier\n) {''',
        '''private fun PlayerSeekProgress(\n    player: androidx.media3.common.Player,\n    mediaSource: String?,\n    progress: Float,\n    isPlaying: Boolean,\n    onScrubbingChanged: (Boolean) -> Unit,\n    onScrubUp: () -> Unit,\n    onScrubToControls: () -> Unit,\n    modifier: Modifier = Modifier\n) {'''
    ),
    (
        '''        scrubbing = true\n        player.pause()''',
        '''        scrubbing = true\n        onScrubbingChanged(true)\n        player.pause()'''
    ),
    (
        '''        scrubbing = false\n\n        // Seeking is an explicit action, so resume playback on commit.\n        player.play()''',
        '''        scrubbing = false\n        onScrubbingChanged(false)\n\n        // Seeking is an explicit action, so resume playback on commit.\n        player.play()'''
    ),
    (
        '''        scrubbing = false\n\n        if (wasPlayingBeforeScrub) {\n            player.play()\n        }''',
        '''        scrubbing = false\n        onScrubbingChanged(false)\n\n        if (wasPlayingBeforeScrub) {\n            player.play()\n        }'''
    ),
    (
        '''                            KeyEvent.KEYCODE_BACK -> {\n                                if (scrubbing) {\n                                    cancelScrubbing()\n                                    true\n                                } else {\n                                    false\n                                }\n                            }\n\n                            else -> false''',
        '''                            KeyEvent.KEYCODE_DPAD_UP -> {\n                                if (scrubbing) {\n                                    cancelScrubbing()\n                                    onScrubUp()\n                                    true\n                                } else {\n                                    false\n                                }\n                            }\n\n                            KeyEvent.KEYCODE_DPAD_DOWN -> {\n                                if (scrubbing) {\n                                    cancelScrubbing()\n                                    onScrubToControls()\n                                    true\n                                } else {\n                                    false\n                                }\n                            }\n\n                            KeyEvent.KEYCODE_BACK -> {\n                                if (scrubbing) {\n                                    cancelScrubbing()\n                                    onScrubToControls()\n                                    true\n                                } else {\n                                    false\n                                }\n                            }\n\n                            else -> false'''
    )
]

for old, new in replacements:
    if text.count(old) != 1:
        raise SystemExit(f'Expected exactly one match, found {text.count(old)}: {old[:100]!r}')
    text = text.replace(old, new, 1)

screen.write_text(text, encoding='utf-8')

vm = Path('app/src/main/java/me/xdan/aperture/ui/screen/player/PlayerViewModel.kt')
text = vm.read_text(encoding='utf-8')
old = '''    fun toggleOsd() {\n        _isOsdVisible.value = !_isOsdVisible.value\n        if (_isOsdVisible.value) resetOsdTimer()\n    }\n    fun hideOsd() { osdTimerJob?.cancel(); _isOsdVisible.value = false }\n    fun showOsdBriefly() { _isOsdVisible.value = true; resetOsdTimer() }\n    private fun resetOsdTimer() {\n        osdTimerJob?.cancel()\n        osdTimerJob = viewModelScope.launch { delay(3000); _isOsdVisible.value = false }\n    }'''
new = '''    fun toggleOsd() {\n        _isOsdVisible.value = !_isOsdVisible.value\n        if (_isOsdVisible.value) resetOsdTimer()\n    }\n    fun hideOsd() { osdTimerJob?.cancel(); _isOsdVisible.value = false }\n    fun showOsdBriefly() { _isOsdVisible.value = true; resetOsdTimer() }\n    fun setScrubbing(scrubbing: Boolean) {\n        if (scrubbing) {\n            osdTimerJob?.cancel()\n        } else {\n            resetOsdTimer()\n        }\n    }\n    private fun resetOsdTimer() {\n        osdTimerJob?.cancel()\n        osdTimerJob = viewModelScope.launch { delay(3000); _isOsdVisible.value = false }\n    }'''
if text.count(old) != 1:
    raise SystemExit(f'Expected exactly one VM match, found {text.count(old)}')
text = text.replace(old, new, 1)
vm.write_text(text, encoding='utf-8')

Path('scripts/apply_scrub_interaction_fix.py').unlink()
Path('.github/workflows/apply-scrub-interaction-fix.yml').unlink()
