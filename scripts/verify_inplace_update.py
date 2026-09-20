#!/usr/bin/env python3
"""Replace the same owner APK without uninstalling; verify app-private markers survive.
This uses the temporary CI signer, not the owner's offline signing key. Never uses an account.
"""
from pathlib import Path
import json
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
APP_ID = 'com.ahmed9461.botos.app'


def single_apk(folder: Path, application_id: str) -> Path:
    metadata = json.loads((folder / 'output-metadata.json').read_text())
    elements = metadata.get('elements', [])
    if metadata.get('applicationId') != application_id or len(elements) != 1:
        raise ValueError('Unexpected update-probe APK metadata')
    name = elements[0].get('outputFile', '')
    if not name.endswith('.apk') or Path(name).name != name or '\\' in name:
        raise ValueError('Invalid update-probe APK path')
    result = folder / name
    if not result.is_file() or result.resolve().parent != folder.resolve():
        raise ValueError('Missing update-probe APK')
    return result


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
    apk = single_apk(ROOT / 'app/build/outputs/apk/ownerPreview', APP_ID)
    test_apk = single_apk(ROOT / 'app/build/outputs/apk/androidTest/ownerPreview', APP_ID + '.test')
    for candidate in (apk, test_apk):
        if 'Success' not in call('install', '-r', '-t', str(candidate), timeout=120):
            raise ValueError('Probe install failed')
    def probe(stage: str) -> None:
        output = call('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                      'com.ahmed9461.botos.OwnerUpdateProbeTest', '-e', 'botos.updateStage', stage,
                      APP_ID + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=120)
        verify_instrumentation(output)
    probe('write')
    if 'Success' not in call('install', '-r', '-t', str(apk), timeout=120):
        raise ValueError('In-place reinstall failed')
    probe('verify')
    destination = ROOT / 'diagnostics/owner-update.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps({
        'application_id': APP_ID, 'in_place_reinstall': 'passed',
        'private_file_preserved': True, 'preferences_preserved': True,
        'uninstalled_between_installs': False, 'same_version_reinstall': True,
        'signer': 'temporary CI', 'owner_final_signer_device_test': False, 'account_used': False,
    }, indent=2) + '\n')
    print('owner_update_persistence=passed; temporary_CI_signer; no_account')


if __name__ == '__main__':
    main()
