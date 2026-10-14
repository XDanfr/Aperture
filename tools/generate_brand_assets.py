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
NS = 'http://www.w3.org/2000/svg'
ET.register_namespace('', NS)
SOURCE = ET.parse(ART / 'aperture-logo.svg').getroot()


def variant(viewbox='0 0 100 100', size=512, mono=False):
    root = deepcopy(SOURCE)
    root.set('viewBox', viewbox)
    root.set('width', str(size))
    root.set('height', str(size))
    if mono:
        for child in list(root):
            if child.get('id') == 'solid-intersections' or child.tag.endswith('defs'):
                root.remove(child)
        for element in root.iter():
            if 'fill' in element.attrib:
                element.set('fill', '#FFFFFF')
        for element in root.iter():
            if element.tag.endswith('desc'):
                element.text = 'Aperture single-colour silhouette for Android themed icons.'
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
banner_svg = header + f'<rect width="1280" height="720" fill="{BACKGROUND}"/>' + banner_body + '</svg>\n'
(ART / 'aperture-banner.svg').write_text(banner_svg)
full_banner = render(banner_svg, 1280)
foreground_banner = render(header + banner_body + '</svg>', 1280)
for p in [MAIN / 'assets/ic_banner.png', MAIN / 'res/drawable/ic_banner.png']:
    save(full_banner, p)
save(foreground_banner, MAIN / 'res/drawable/ic_banner_foreground.png')
save(full_banner.resize((320, 180), Image.Resampling.LANCZOS), MAIN / 'res/mipmap-xhdpi/ic_banner.png')
save(foreground_banner.resize((320, 180), Image.Resampling.LANCZOS), MAIN / 'res/mipmap-xhdpi/ic_banner_foreground.png')
for name in ['ic_launcher_background', 'ic_banner_background']:
    (MAIN / 'res/values' / f'{name}.xml').write_text(
        f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="{name}">{BACKGROUND}</color>\n</resources>\n')
for svg in ART.glob('*.svg'):
    svg.write_text('\n'.join(line.rstrip() for line in svg.read_text().splitlines()) + '\n')
print('Regenerated launcher, monochrome, legacy and TV banner assets from artwork/aperture-logo.svg.')
