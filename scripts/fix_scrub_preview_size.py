from pathlib import Path

path = Path('app/src/main/java/me/xdan/aperture/ui/screen/player/PlayerScreen.kt')
text = path.read_text(encoding='utf-8')

old = '''    BoxWithConstraints(
        modifier = modifier
            .onFocusChanged {
'''
new = '''    BoxWithConstraints(
        modifier = modifier
            .layout { measurable, constraints ->
                val relaxedConstraints = constraints.copy(
                    maxHeight = maxOf(constraints.maxHeight, 240.dp.roundToPx())
                )
                val placeable = measurable.measure(relaxedConstraints)
                val reportedHeight = constraints.constrainHeight(24.dp.roundToPx())

                layout(constraints.maxWidth, reportedHeight) {
                    placeable.placeRelative(
                        x = 0,
                        y = (reportedHeight - placeable.height) / 2
                    )
                }
            }
            .onFocusChanged {
'''
if text.count(old) != 1:
    raise SystemExit(f'Expected one BoxWithConstraints match, found {text.count(old)}')
text = text.replace(old, new, 1)

path.write_text(text, encoding='utf-8')
Path('scripts/fix_scrub_preview_size.py').unlink()
Path('.github/workflows/fix-scrub-preview-size.yml').unlink()
