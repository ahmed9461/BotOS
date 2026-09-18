#!/usr/bin/env python3
"""Static safety checks complement, but never replace, device UI regression tests."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
icons = [
    'app/src/main/res/drawable/ic_launcher_foreground.xml',
    'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
    'app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml',
    'app/src/main/res/values/colors.xml',
]
for path in icons:
    original = subprocess.run(['git', 'show', f'914ab1363c8befa606a47defb53268d372b809d1:{path}'], cwd=root, check=True, capture_output=True).stdout
    if (root / path).read_bytes() != original:
        raise SystemExit(f'Approved launcher artwork changed: {path}')
for file in ['WorkspaceScreen.kt', 'SettingsScreens.kt', 'AccountScreen.kt', 'LiveBotPanel.kt']:
    code = (root / 'app/src/main/kotlin/com/ahmed9461/botos' / file).read_text()
    if '.imePadding(' in code or '.navigationBarsPadding(' in code:
        raise SystemExit(f'Insets must be owned by the application shell, not {file}')
for folder in ['values', 'values-en']:
    path = root / 'app/src/main/res' / folder / 'ui_refinement.xml'
    names = {node.attrib['name'] for node in ET.parse(path).getroot()}
    if folder == 'values': expected = names
    elif expected != names: raise SystemExit('Refinement translations do not match')
print('Approved launcher unchanged; single-inset ownership and AR/EN resource parity passed.')
for resource in ['account.xml', 'live_bots.xml']:
    ar = {node.attrib['name'] for node in ET.parse(root / 'app/src/main/res/values' / resource).getroot()}
    en = {node.attrib['name'] for node in ET.parse(root / 'app/src/main/res/values-en' / resource).getroot()}
    if ar != en: raise SystemExit('Account or bot translations do not match')
account = (root / 'app/src/main/kotlin/com/ahmed9461/botos/AccountScreen.kt').read_text()
if 'by rememberSaveable' in account or 'SavedStateHandle' in account:
    raise SystemExit('Do not persist account input in saved UI state')
print('Account resource parity and transient input guard passed.')
