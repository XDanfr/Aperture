from pathlib import Path

path = Path('app/src/main/java/me/xdan/aperture/ui/screen/player/PlayerScreen.kt')
text = path.read_text(encoding='utf-8')
needle = 'import androidx.compose.ui.input.key.onPreviewKeyEvent\n'
replacement = needle + 'import androidx.compose.ui.layout.layout\n'
if 'import androidx.compose.ui.layout.layout\n' not in text:
    if text.count(needle) != 1:
        raise SystemExit('Could not find the expected Compose key input import')
    text = text.replace(needle, replacement, 1)
path.write_text(text, encoding='utf-8')
Path('scripts/fix_scrub_preview_layout_import.py').unlink()
Path('.github/workflows/fix-scrub-preview-layout-import.yml').unlink()
