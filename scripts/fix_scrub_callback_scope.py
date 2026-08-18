from pathlib import Path

p = Path('app/src/main/java/me/xdan/aperture/ui/screen/player/PlayerScreen.kt')
s = p.read_text()

repls = [
('''                onQuickMenu = {\n                    isQuickMenuVisible = true\n                    viewModel.toggleOsd()\n                }\n''', '''                onQuickMenu = {\n                    isQuickMenuVisible = true\n                    viewModel.toggleOsd()\n                },\n                onScrubbingChanged = viewModel::setScrubbing,\n                onScrubUp = viewModel::hideOsd,\n                onScrubToControls = controlsFocusRequester::requestFocus\n'''),
('''    onInteraction: () -> Unit,\n    onRestart: () -> Unit,\n    onQuickMenu: () -> Unit\n) {''', '''    onInteraction: () -> Unit,\n    onRestart: () -> Unit,\n    onQuickMenu: () -> Unit,\n    onScrubbingChanged: (Boolean) -> Unit,\n    onScrubUp: () -> Unit,\n    onScrubToControls: () -> Unit\n) {'''),
('''                onScrubbingChanged = viewModel::setScrubbing,\n                onScrubUp = viewModel::hideOsd,\n                onScrubToControls = controlsFocusRequester::requestFocus,\n''', '''                onScrubbingChanged = onScrubbingChanged,\n                onScrubUp = onScrubUp,\n                onScrubToControls = onScrubToControls,\n''')
]

for old, new in repls:
    if s.count(old) != 1:
        raise SystemExit(f'Expected 1 match, found {s.count(old)}: {old[:120]!r}')
    s = s.replace(old, new, 1)

p.write_text(s)
Path('scripts/fix_scrub_callback_scope.py').unlink()
Path('.github/workflows/fix-scrub-callback-scope.yml').unlink()
