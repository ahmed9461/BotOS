#!/usr/bin/env python3
"""Require genuine collected screenshots and the real-IME measurement before APK publication."""
from pathlib import Path
import math
import shutil
import struct

root = Path(__file__).resolve().parents[1]
outputs = root / 'app/build/outputs'
destination = root / 'diagnostics/ui/screenshots'
destination.mkdir(parents=True, exist_ok=True)
required = ['appearance-light-ar.png', 'appearance-dark-ar.png', 'appearance-restored-ar.png',
            'workspace-ar.png', 'keyboard-ar.png', 'library-ar.png', 'editor-ar.png', 'keyboard-gap.txt']
for name in required:
    matches = [p for p in outputs.rglob(name) if p.is_file() and 'additional_output' in str(p)]
    if len(matches) != 1:
        raise SystemExit(f'Expected one collected test output {name}, got {len(matches)}')
    data = matches[0].read_bytes()
    if name.endswith('.png'):
        if data[:8] != b'\x89PNG\r\n\x1a\n' or len(data) < 24:
            raise SystemExit(f'Invalid PNG evidence: {name}')
        width, height = struct.unpack('>II', data[16:24])
        if width < 300 or height < 300:
            raise SystemExit(f'Unexpected screenshot dimensions: {name}: {width}x{height}')
        print(f'{name}: {width}x{height}')
    else:
        fields = dict(line.split('=', 1) for line in data.decode().splitlines() if '=' in line)
        gap = float(fields['gapDp'])
        if not math.isfinite(gap) or not -2 <= gap <= 12:
            raise SystemExit(f'Keyboard gap outside accepted range: {gap}dp')
        print(f'Real keyboard gap: {gap} dp')
    shutil.copyfile(matches[0], destination / name)
print('All seven device screenshots and keyboard measurement were collected.')
