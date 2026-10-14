#!/usr/bin/env python3
"""Regenerate Aperture's launcher and TV branding. Requires Inkscape, Pillow, fontTools."""
from copy import deepcopy
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main'
ART = ROOT / 'artwork'
BACKGROUND = '#111014'
PRIMARY = '#D0BCFF'
MONO_PLAY = '#FFFFFF'
MONO_BLADE = '#B0B0B0'
MONO_OVERLAP = '#303030'
NS = 'http://www.w3.org/2000/svg'
ET.register_namespace('', NS)
SOURCE = ET.parse(ART / 'aperture-logo.svg').getroot()


def variant(viewbox='0 0 100 100', size=512, mono=False):
    root = deepcopy(SOURCE)
    root.set('viewBox', viewbox)
    root.set('width', str(size))
    root.set('height', str(size))
    if mono:
        for element in root.iter():
            element_id = element.get('id')
            if element_id and element_id.startswith('blade-'):
                element.set('fill', MONO_BLADE)
            elif element_id == 'solid-intersections':
                element.set('fill', MONO_OVERLAP)
            elif element_id == 'play':
                element.set('fill', MONO_PLAY)
            elif element.tag.endswith('desc'):
                element.text = (
                    'Aperture grayscale monochrome icon: white play symbol, '
                    'grey shutter blades and dark intersections. Android launchers '
                    'may apply one theme tint to the monochrome adaptive-icon layer.'
                )
    return '\n'.join(line.rstrip() for line in ET.tostring(root, encoding='unicode').splitlines())


def render(svg, size):
    with tempfile.TemporaryDirectory() as directory:
        src = Path(directory) / 'source.svg'
        dst = Path(directory) / 'render.png'
        src.write_text(svg)
        subprocess.run(['inkscape', str(src), '--export-type=png',
                        f'--export-filename={dst}', f'--export-width={size}'],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return Image.open(dst).convert('RGBA')


def tile(mark, size, shape='rounded'):
    result = Image.new('RGBA', (size, size))
    draw = ImageDraw.Draw(result)
    bounds = (0, 0, size - 1, size - 1)
    if shape == 'circle':
        draw.ellipse(bounds, fill=BACKGROUND)
    elif shape == 'square':
        draw.rectangle(bounds, fill=BACKGROUND)
    else:
        draw.rounded_rectangle(bounds, radius=round(size * .22), fill=BACKGROUND)
    dimension = round(size * .76)
    scaled = mark.resize((dimension, dimension), Image.Resampling.LANCZOS)
    result.alpha_composite(scaled, ((size - dimension) // 2, (size - dimension) // 2))
    return result


def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, lossless=True) if path.suffix == '.webp' else image.save(path)


logo = render(variant(), 1024)
foreground = render(variant('-38 -38 176 176', 432), 432)
mono = render(variant('-38 -38 176 176', 432, mono=True), 432)
(ART / 'aperture-logo-monochrome.svg').write_text(variant(mono=True) + '\n')
(ART / 'aperture-launcher-foreground.svg').write_text(variant('-38 -38 176 176', 432) + '\n')
(ART / 'aperture-launcher-monochrome.svg').write_text(variant('-38 -38 176 176', 432, mono=True) + '\n')
# Retain the old path as a compatibility copy for existing external links.
(ART / 'aperture-logo-glass.svg').write_text((ART / 'aperture-logo.svg').read_text())
for density, size, layer in [('mdpi', 48, 108), ('hdpi', 72, 162), ('xhdpi', 96, 216),
                             ('xxhdpi', 144, 324), ('xxxhdpi', 192, 432)]:
    resource = MAIN / 'res' / f'mipmap-{density}'
    exported = MAIN / 'assets/res' / f'mipmap-{density}'
    save(tile(logo, size), resource / 'ic_launcher.webp')
    save(tile(logo, size, 'circle'), resource / 'ic_launcher_round.webp')
    save(foreground.resize((layer, layer), Image.Resampling.LANCZOS), resource / 'ic_launcher_foreground.webp')
    save(mono.resize((layer, layer), Image.Resampling.LANCZOS), resource / 'ic_launcher_monochrome.webp')
    save(tile(logo, size), exported / 'ic_launcher.png')
    save(Image.new('RGBA', (layer, layer), BACKGROUND), exported / 'ic_launcher_background.png')
    save(foreground.resize((layer, layer), Image.Resampling.LANCZOS), exported / 'ic_launcher_foreground.png')
    save(mono.resize((layer, layer), Image.Resampling.LANCZOS), exported / 'ic_launcher_monochrome.png')
save(tile(logo, 512, 'square'), MAIN / 'ic_launcher-playstore.png')
save(tile(logo, 1024), ROOT / 'Aperture.png')

# The banner's wordmark is vector outlines from the app's own Outfit SemiBold font.
font = TTFont(MAIN / 'res/font/outfit_semibold.ttf')
glyphs = font.getGlyphSet()
cmap = font.getBestCmap()
advance = 0
word = []
for char in 'Aperture':
    glyph_name = cmap[ord(char)]
    pen = SVGPathPen(glyphs)
    glyphs[glyph_name].draw(pen)
    word.append(f'<path d="{pen.getCommands()}" transform="translate({advance} 0)"/>')
    advance += font['hmtx'][glyph_name][0]
scale = 640 / advance
root = deepcopy(SOURCE)
logo_parts = ''.join(ET.tostring(e, encoding='unicode') for e in root if e.tag.endswith(('defs', 'g', 'path')))
banner_body = (f'<g transform="translate(136 200) scale(3.2)">{logo_parts}</g>'
               f'<g fill="{PRIMARY}" transform="translate(512 403) scale({scale} {-scale})">' + ''.join(word) + '</g>')
header = f'<svg xmlns="{NS}" width="1280" height="720" viewBox="0 0 1280 720" role="img" aria-label="Aperture">'
banner_background_body = '''<defs>
  <linearGradient id="purple-spectrum" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#111014"/><stop offset="0.34" stop-color="#211A2E"/>
    <stop offset="0.68" stop-color="#381E72"/><stop offset="1" stop-color="#6750A4"/>
  </linearGradient>
  <radialGradient id="purple-halo" cx="0.94" cy="0.16" r="0.72">
    <stop offset="0" stop-color="#D0BCFF" stop-opacity="0.48"/><stop offset="0.52" stop-color="#6750A4" stop-opacity="0.24"/>
    <stop offset="1" stop-color="#6750A4" stop-opacity="0"/>
  </radialGradient>
</defs>
<rect width="1280" height="720" fill="#111014"/>
<rect width="1280" height="720" fill="url(#purple-spectrum)"/>
<rect width="1280" height="720" fill="url(#purple-halo)"/>
<path d="M0 594 C218 538 310 659 515 720 H0Z" fill="#381E72" opacity="0.72"/>
<path d="M1000 0 H1280 V342 C1168 298 1138 158 1000 0Z" fill="#D0BCFF" opacity="0.12"/>'''
banner_background_svg = header + banner_background_body + '</svg>\n'
banner_svg = header + banner_background_body + banner_body + '</svg>\n'
(ART / 'aperture-banner.svg').write_text(banner_svg)
(ART / 'aperture-banner-background.svg').write_text(banner_background_svg)
full_banner = render(banner_svg, 1280)
foreground_banner = render(header + banner_body + '</svg>', 1280)
# AdaptiveIconDrawable expands each child by 25% on every side (1.5x total).
# Expand the source viewport by the same amount so the visible result matches
# the flat banner. Only launcher foregrounds get this padding; onboarding uses
# the full-size drawable foreground below.
adaptive_header = header.replace('viewBox="0 0 1280 720"',
                                 'viewBox="-320 -180 1920 1080"')
adaptive_banner_svg = adaptive_header + banner_body + '</svg>\n'
(ART / 'aperture-banner-foreground.svg').write_text(adaptive_banner_svg)
adaptive_foreground_banner = render(adaptive_banner_svg, 1280)
adaptive_background_svg = (adaptive_header
                           + f'<rect x="-320" y="-180" width="1920" height="1080" fill="{BACKGROUND}"/>'
                           + banner_background_body + '</svg>\n')
(ART / 'aperture-banner-background-adaptive.svg').write_text(adaptive_background_svg)
adaptive_background_banner = render(adaptive_background_svg, 1280)
for p in [MAIN / 'assets/ic_banner.png', MAIN / 'res/drawable/ic_banner.png']:
    save(full_banner, p)
save(foreground_banner, MAIN / 'res/drawable/ic_banner_foreground.png')
for density, size in [('mdpi', (160, 90)), ('hdpi', (240, 135)), ('xhdpi', (320, 180)),
                      ('xxhdpi', (480, 270)), ('xxxhdpi', (640, 360))]:
    resource = MAIN / 'res' / f'mipmap-{density}'
    save(full_banner.resize(size, Image.Resampling.LANCZOS), resource / 'ic_banner.png')
    save(adaptive_foreground_banner.resize(size, Image.Resampling.LANCZOS),
         resource / 'ic_banner_foreground.png')
    save(adaptive_background_banner.resize(size, Image.Resampling.LANCZOS),
         resource / 'ic_banner_background_art.png')
for name in ['ic_launcher_background']:
    (MAIN / 'res/values' / f'{name}.xml').write_text(
        f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="{name}">{BACKGROUND}</color>\n</resources>\n')
for svg in ART.glob('*.svg'):
    svg.write_text('\n'.join(line.rstrip() for line in svg.read_text().splitlines()) + '\n')
print('Regenerated launcher, monochrome, legacy and layered TV banner assets from artwork/aperture-logo.svg.')
