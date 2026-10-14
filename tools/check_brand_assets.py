#!/usr/bin/env python3
"""Check TV banner sizes and Android's enlarged adaptive foreground viewport."""
from pathlib import Path
import unittest

from PIL import Image

RES = Path(__file__).resolve().parents[1] / 'app/src/main/res'
SIZES = {'mdpi': (160, 90), 'hdpi': (240, 135), 'xhdpi': (320, 180),
         'xxhdpi': (480, 270), 'xxxhdpi': (640, 360)}


def ink_bounds(image):
    return image.getchannel('A').point(lambda alpha: 255 if alpha >= 32 else 0).getbbox()


def adaptive_viewport(image):
    # AdaptiveIconDrawable.getExtraInsetFraction() adds 25% on each side.
    width, height = image.size
    expanded = image.resize((round(width * 1.5), round(height * 1.5)), Image.Resampling.LANCZOS)
    left, top = (expanded.width - width) // 2, (expanded.height - height) // 2
    return expanded.crop((left, top, left + width, top + height))


class BannerAssetsTest(unittest.TestCase):
    def test_each_density_preserves_the_complete_visible_banner(self):
        full_artwork = Image.open(RES / 'drawable/ic_banner_foreground.png').convert('RGBA')
        for density, size in SIZES.items():
            with self.subTest(density=density):
                folder = RES / f'mipmap-{density}'
                foreground = Image.open(folder / 'ic_banner_foreground.png').convert('RGBA')
                flat = Image.open(folder / 'ic_banner.png').convert('RGBA')
                self.assertEqual(size, foreground.size)
                self.assertEqual(size, flat.size)
                self.assertEqual((255, 255), flat.getchannel('A').getextrema())
                bounds = ink_bounds(foreground)
                self.assertIsNotNone(bounds)
                # Content must fit the centre two-thirds before Android's enlargement.
                self.assertGreaterEqual(bounds[0], size[0] / 6)
                self.assertGreaterEqual(bounds[1], size[1] / 6)
                self.assertLessEqual(bounds[2], size[0] * 5 / 6)
                self.assertLessEqual(bounds[3], size[1] * 5 / 6)
                expected = ink_bounds(full_artwork.resize(size, Image.Resampling.LANCZOS))
                actual = ink_bounds(adaptive_viewport(foreground))
                for wanted, rendered in zip(expected, actual):
                    self.assertAlmostEqual(wanted, rendered, delta=2)
                # Leave visible margins after enlargement; neither end may touch the mask.
                self.assertGreater(actual[0], size[0] * 0.06)
                self.assertLess(actual[2], size[0] * 0.94)

    def test_launcher_uses_the_padded_foreground(self):
        import xml.etree.ElementTree as ET
        root = ET.parse(RES / 'mipmap-anydpi-v26/ic_banner.xml').getroot()
        self.assertEqual('@mipmap/ic_banner_foreground', root.find('foreground').get(
            '{http://schemas.android.com/apk/res/android}drawable'))


if __name__ == '__main__':
    unittest.main()
