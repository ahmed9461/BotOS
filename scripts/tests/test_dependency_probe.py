import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('probe_dependencies', Path(__file__).resolve().parents[1] / 'probe_dependencies.py')
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)

class MetadataTest(unittest.TestCase):
    XML = '<metadata><versioning><versions><version>1.1.0</version><version>1.2.0-rc01</version><version>1.2.0</version></versions></versioning></metadata>'
    def test_exact_publication_and_stable_filter(self):
        result = probe.describe_metadata(self.XML, '1.2.0')
        self.assertTrue(result['published'])
        self.assertEqual(result['recent_stable'], ['1.1.0', '1.2.0'])
    def test_missing_version_is_not_implicitly_replaced(self):
        result = probe.describe_metadata(self.XML, '1.3.0')
        self.assertFalse(result['published'])
        self.assertEqual(result['selected'], '1.3.0')
    def test_invalid_xml_is_not_treated_as_empty_publication(self):
        with self.assertRaises(ET.ParseError):
            probe.describe_metadata('unavailable', '1.3.0')

if __name__ == '__main__':
    unittest.main()
