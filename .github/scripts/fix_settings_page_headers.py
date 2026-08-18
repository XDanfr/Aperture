from pathlib import Path
import re

path = Path('app/src/main/java/me/xdan/aperture/ui/screen/settings/SettingsScreen.kt')
text = path.read_text(encoding='utf-8')

pattern = re.compile(
    r'(SettingsPage\.(?:CUSTOMISATION|LIBRARY|PLAYBACK|ABOUT) -> \{\n)'
    r'(\s*)SettingsPageHeader\(\n'
    r'(.*?)\n\2\)\n',
    re.DOTALL,
)

matches = list(pattern.finditer(text))
if len(matches) != 4:
    raise SystemExit(f'Expected 4 page headers, found {len(matches)}')

text = pattern.sub(
    lambda m: (
        m.group(1)
        + m.group(2)
        + 'item {\n'
        + m.group(2)
        + '    SettingsPageHeader(\n'
        + m.group(3)
        + '\n'
        + m.group(2)
        + '    )\n'
        + m.group(2)
        + '}\n'
    ),
    text,
)

path.write_text(text, encoding='utf-8')
print('Wrapped all page headers in LazyColumn item blocks.')
