"""Check every bundled native ELF and require paired ARM ABIs in the release AAB."""
from pathlib import Path
import struct
import zipfile

bundle = Path('app/build/outputs/bundle/playRelease/app-play-release.aab')
abis = set()
count = 0
with zipfile.ZipFile(bundle) as archive:
    for name in archive.namelist():
        if not name.endswith('.so'):
            continue
        data = archive.read(name)
        assert data[:4] == b'\x7fELF', name
        abis.add(name.split('/')[-2])
        wide = data[4] == 2
        endian = '<' if data[5] == 1 else '>'
        offset = struct.unpack_from(endian + ('Q' if wide else 'I'), data, 32 if wide else 28)[0]
        size, number = struct.unpack_from(endian + 'HH', data, 54 if wide else 42)
        for index in range(number):
            header = offset + index * size
            if struct.unpack_from(endian + 'I', data, header)[0] == 1:
                alignment = struct.unpack_from(endian + ('Q' if wide else 'I'), data, header + (48 if wide else 28))[0]
                assert alignment >= 16384, f'{name}: LOAD alignment {alignment}'
        count += 1
assert {'armeabi-v7a', 'arm64-v8a'} <= abis, abis
print(f'{count} ELF files pass 16 KB LOAD alignment; ABIs: {sorted(abis)}')
