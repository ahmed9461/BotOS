"""Do not accept the negative unfinished duration observed in interrupted CI34 XML."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('owner_startup_completion', Path(__file__).resolve().parents[1] / 'owner_delivery.py')
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)

class StartupCompletionTest(unittest.TestCase):
    def test_unfinished_or_nonfinite_duration_is_not_a_success(self):
        with tempfile.TemporaryDirectory() as temp:
            report = Path(temp) / 'TEST-owner.xml'
            for elapsed in ['-1.789769163909E9', 'nan', 'inf', '-1']:
                report.write_text('<testsuites tests="1" failures="0"><testsuite tests="1" failures="0">'
                    '<testcase classname="com.ahmed9461.botos.OwnerBuildSmokeTest" '
                    'name="startupRequiresConsentBeforeCreatingAnySession" '
                    f'time="{elapsed}" /></testsuite></testsuites>')
                with self.subTest(elapsed=elapsed), self.assertRaises(ValueError):
                    module.validate_startup_report(report)
