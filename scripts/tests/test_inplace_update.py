import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('update_probe', Path(__file__).resolve().parents[1] / 'verify_inplace_update.py')
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)

class InPlaceUpdateTest(unittest.TestCase):
    def test_instrumentation_requires_actual_success(self):
        probe.verify_instrumentation('OK (1 test)\nINSTRUMENTATION_CODE: -1\n')
        for value in ['OK (0 tests)', 'INSTRUMENTATION_CODE: 0', 'OK (1 test)\nINSTRUMENTATION_CODE: -1\nFAILURES!!!']:
            with self.assertRaises(ValueError): probe.verify_instrumentation(value)

    def test_metadata_requires_owner_identity_and_non_escaping_single_apk(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            (folder / 'actual.apk').write_bytes(b'fixture')
            metadata = {'applicationId': probe.APP_ID, 'elements': [{'outputFile':'actual.apk'}]}
            (folder / 'output-metadata.json').write_text(json.dumps(metadata))
            self.assertEqual(folder / 'actual.apk', probe.single_apk(folder, probe.APP_ID))
            for bad in ['../actual.apk', '/tmp/a.apk', 'bad\\a.apk', 'missing.apk']:
                metadata['elements'][0]['outputFile'] = bad
                (folder / 'output-metadata.json').write_text(json.dumps(metadata))
                with self.assertRaises(ValueError): probe.single_apk(folder, probe.APP_ID)
