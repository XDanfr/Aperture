from pathlib import Path

path = Path('app/src/main/java/me/xdan/aperture/ui/screen/player/PlayerScreen.kt')
text = path.read_text(encoding='utf-8')
old = '                val reportedHeight = constraints.constrainHeight(24.dp.roundToPx())'
new = '                val reportedHeight = 24.dp.roundToPx()\n                    .coerceIn(constraints.minHeight, constraints.maxHeight)'
if text.count(old) != 1:
    raise SystemExit(f'Expected one match, found {text.count(old)}')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
Path('scripts/fix_preview_constrain_height.py').unlink()
