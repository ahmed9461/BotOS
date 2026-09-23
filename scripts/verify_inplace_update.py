#!/usr/bin/env python3
"""Upgrade ownerPreview 0.4 to 0.5 without uninstalling; verify private markers.
This uses the temporary CI signer, not the owner's offline signing key. Never uses an account.
"""
from pathlib import Path
import json
import os
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
APP_ID = 'com.ahmed9461.botos.app'


def single_apk(folder: Path, application_id: str, version: tuple[int, str] | None = None) -> Path:
    metadata = json.loads((folder / 'output-metadata.json').read_text())
    elements = metadata.get('elements', [])
    if metadata.get('applicationId') != application_id or len(elements) != 1:
        raise ValueError('Unexpected update-probe APK metadata')
    if version is not None and (elements[0].get('versionCode'), elements[0].get('versionName')) != version:
        raise ValueError('Unexpected update-probe APK version')
    name = elements[0].get('outputFile', '')
    if not name.endswith('.apk') or Path(name).name != name or '\\' in name:
        raise ValueError('Invalid update-probe APK path')
    result = folder / name
    if not result.is_file() or result.resolve().parent != folder.resolve():
        raise ValueError('Missing update-probe APK')
    return result


def signer_digest(apk: Path, apksigner: Path) -> str:
    result = subprocess.run([str(apksigner), 'verify', '--print-certs', str(apk)],
                            check=True, capture_output=True, text=True, timeout=60)
    matches = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$',
                         result.stdout, re.M)
    if len(matches) != 1:
        raise ValueError('Expected one verified APK signer')
    return matches[0].lower()


def verify_instrumentation(text: str) -> None:
    if ('OK (1 test)' not in text or 'INSTRUMENTATION_CODE: -1' not in text or
            'FAILURES' in text or 'INSTRUMENTATION_FAILED' in text or 'shortMsg=' in text):
        raise ValueError('Update persistence instrumentation did not pass')


def main() -> None:
    adb = Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb'
    serial = os.environ.get('ANDROID_SERIAL', '')
    if serial != 'emulator-5554':
        raise ValueError('Disposable emulator required')
    def call(*args: str, timeout: int = 60) -> str:
        result = subprocess.run([str(adb), '-s', serial, *args], check=True,
                                capture_output=True, text=True, timeout=timeout)
        return result.stdout
    if call('shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
        raise ValueError('Not an emulator')
    previous_folder = Path(os.environ['BOTOS_PREVIOUS_OWNER_FOLDER']).resolve()
    apk_previous = single_apk(previous_folder, APP_ID, (4, '0.4.0-preview'))
    apk = single_apk(ROOT / 'app/build/outputs/apk/ownerPreview', APP_ID,
                     (5, '0.5.0-preview'))
    test_apk = single_apk(ROOT / 'app/build/outputs/apk/androidTest/ownerPreview', APP_ID + '.test')
    apksigner = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0/apksigner'
    if signer_digest(apk_previous, apksigner) != signer_digest(apk, apksigner):
        raise ValueError('Upgrade APK signers differ')
    for candidate in (apk_previous, test_apk):
        if 'Success' not in call('install', '-r', '-t', str(candidate), timeout=120):
            raise ValueError('Probe install failed')
    def probe(stage: str) -> None:
        output = call('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                      'com.ahmed9461.botos.OwnerUpdateProbeTest', '-e', 'botos.updateStage', stage,
                      APP_ID + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=120)
        verify_instrumentation(output)
    probe('write')
    if 'Success' not in call('install', '-r', '-t', str(apk), timeout=120):
        raise ValueError('In-place upgrade failed')
    probe('verify')
    destination = ROOT / 'diagnostics/owner-update.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps({
        'application_id': APP_ID, 'in_place_upgrade': 'passed',
        'from_version': '0.4.0-preview', 'to_version': '0.5.0-preview',
        'private_file_preserved': True, 'preferences_preserved': True,
        'uninstalled_between_installs': False, 'same_version_reinstall': False,
        'signer': 'temporary CI', 'owner_final_signer_device_test': False, 'account_used': False,
    }, indent=2) + '\n')
    print('owner_upgrade_0.4_to_0.5=passed; temporary_CI_signer; no_account')


if __name__ == '__main__':
    main()
