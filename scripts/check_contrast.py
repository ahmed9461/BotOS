#!/usr/bin/env python3
"""Checks the actual semantic color pairs used by the design system."""
import re
from pathlib import Path
text = (Path(__file__).resolve().parents[1] / 'core/designsystem/src/main/kotlin/com/ahmed9461/botos/design/BotOsTheme.kt').read_text()
def luminance(hex_color):
    rgb = [int(hex_color[i:i+2], 16) / 255 for i in (0, 2, 4)]
    rgb = [v/12.92 if v <= .04045 else ((v+.055)/1.055)**2.4 for v in rgb]
    return sum(v*w for v,w in zip(rgb, (.2126,.7152,.0722)))
checks = 0
for theme in ['Light', 'Dark']:
    section = re.search(rf'private val {theme}Colors = .*?\((.*?)\n\)', text, re.S).group(1)
    colors = dict(re.findall(r'(\w+) = Color\(0xFF([A-Fa-f0-9]{6})\)', section))
    colors.update({name:'FFFFFF' for name in re.findall(r'(\w+) = Color.White', section)})
    for background in ['primary','primaryContainer','secondary','secondaryContainer','background','surface','surfaceVariant']:
        foreground = 'on' + background[0].upper() + background[1:]
        a,b = sorted([luminance(colors[background]), luminance(colors[foreground])])
        ratio = (b+.05)/(a+.05)
        assert ratio >= 4.5, f'{theme} {foreground}/{background}: {ratio:.2f}'
        print(f'{theme} {foreground}/{background}: {ratio:.2f}:1')
        checks += 1
print(f'{checks} color contrast checks passed (normal text >= 4.5:1).')
