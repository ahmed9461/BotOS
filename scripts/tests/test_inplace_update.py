import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import subprocess

spec = importlib.util.spec_from_file_location('update_probe', Path(__file__).resolve().parents[1] / 'verify_inplace_update.py')
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)

class InPlaceUpdateTest(unittest.TestCase):
    def write_apk(self, folder, app_id, name, version=None):
        folder.mkdir(parents=True)
        (folder / name).write_bytes(b'fixture')
        element = {'outputFile': name}
        if version:
            element.update(versionCode=version[0], versionName=version[1])
        (folder / 'output-metadata.json').write_text(json.dumps({
            'applicationId': app_id, 'elements': [element]}))
        return folder / name

    def test_instrumentation_requires_actual_success(self):
        probe.verify_instrumentation('OK (1 test)\nINSTRUMENTATION_CODE: -1\n')
        for value in ['OK (0 tests)', 'INSTRUMENTATION_CODE: 0', 'OK (1 test)\nINSTRUMENTATION_CODE: -1\nFAILURES!!!']:
            with self.assertRaises(ValueError): probe.verify_instrumentation(value)

    def test_metadata_requires_owner_identity_and_non_escaping_single_apk(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            with self.assertRaises(FileNotFoundError):
                probe.single_apk(folder, probe.APP_ID, (4, '0.4.0-preview'))
            (folder / 'actual.apk').write_bytes(b'fixture')
            metadata = {'applicationId': probe.APP_ID, 'elements': [{
                'outputFile':'actual.apk', 'versionCode':4, 'versionName':'0.4.0-preview'}]}
            (folder / 'output-metadata.json').write_text(json.dumps(metadata))
            self.assertEqual(folder / 'actual.apk', probe.single_apk(folder, probe.APP_ID,
                                                                      (4, '0.4.0-preview')))
            with self.assertRaises(ValueError):
                probe.single_apk(folder, probe.APP_ID, (5, '0.5.0-preview'))
            for bad in ['../actual.apk', '/tmp/a.apk', 'bad\\a.apk', 'missing.apk']:
                metadata['elements'][0]['outputFile'] = bad
                (folder / 'output-metadata.json').write_text(json.dumps(metadata))
                with self.assertRaises(ValueError): probe.single_apk(folder, probe.APP_ID)

    def test_real_upgrade_order_and_signer_gate(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            old_folder = root / 'previous'
            old = self.write_apk(old_folder, probe.APP_ID, 'old.apk', (4, '0.4.0-preview'))
            new = self.write_apk(root / 'app/build/outputs/apk/ownerPreview', probe.APP_ID,
                                 'new.apk', (5, '0.5.0-preview'))
            test = self.write_apk(root / 'app/build/outputs/apk/androidTest/ownerPreview',
                                  probe.APP_ID + '.test', 'test.apk')
            environment = {'ANDROID_HOME': str(root / 'sdk'), 'ANDROID_SERIAL': 'emulator-5554',
                           'BOTOS_PREVIOUS_OWNER_FOLDER': str(old_folder)}
            calls = []
            def run(command, **kwargs):
                calls.append(command)
                if command[0].endswith('apksigner'):
                    digest = 'a' * 64 if command[-1] == str(old) else 'b' * 64
                    if same_signer:
                        digest = 'a' * 64
                    return subprocess.CompletedProcess(command, 0,
                        f'Signer #1 certificate SHA-256 digest: {digest}\n', '')
                if command[-2:] == ['getprop', 'ro.kernel.qemu']:
                    return subprocess.CompletedProcess(command, 0, '1\n', '')
                if 'install' in command:
                    return subprocess.CompletedProcess(command, 0, 'Success\n', '')
                return subprocess.CompletedProcess(command, 0,
                    'OK (1 test)\nINSTRUMENTATION_CODE: -1\n', '')
            same_signer = False
            with patch.object(probe, 'ROOT', root), patch.dict(probe.os.environ, environment), \
                    patch.object(probe.subprocess, 'run', side_effect=run):
                with self.assertRaisesRegex(ValueError, 'signers differ'):
                    probe.main()
                self.assertFalse(any('install' in c for c in calls))
                calls.clear()
                same_signer = True
                probe.main()
            steps = [c for c in calls if 'install' in c or 'instrument' in c]
            self.assertEqual([str(old), str(test), 'write', str(new), 'verify'],
                [c[-1] if 'install' in c else c[c.index('botos.updateStage') + 1] for c in steps])
            self.assertFalse(any('uninstall' in c for c in calls))
            report = json.loads((root / 'diagnostics/owner-update.json').read_text())
            self.assertEqual(('0.4.0-preview', '0.5.0-preview'),
                             (report['from_version'], report['to_version']))
            self.assertFalse(report['same_version_reinstall'])
