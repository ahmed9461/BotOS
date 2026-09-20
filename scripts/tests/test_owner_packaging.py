"""Synthetic packaging fixtures, including AGP's real nested XML layout. No credentials."""
import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zipfile

spec = importlib.util.spec_from_file_location('owner_packaging', Path(__file__).resolve().parents[1] / 'owner_delivery.py')
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
CASE = '<testcase classname="com.ahmed9461.botos.OwnerBuildSmokeTest" name="startupRequiresConsentBeforeCreatingAnySession" />'
FLAT = '<testsuite tests="1" failures="0" errors="0" skipped="0">' + CASE + '</testsuite>'
NESTED = '<testsuites tests="1" failures="0" errors="0" skipped="0">' + FLAT + '</testsuites>'


class OwnerPackagingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name); self.outputs = self.root / 'app/build/outputs'
        self.report = self.outputs / 'androidTest-results/connected/ownerPreview/TEST-device.xml'
        self.report.parent.mkdir(parents=True); self.report.write_text(NESTED)
        self.folder = self.outputs / 'apk/ownerPreview'; self.folder.mkdir(parents=True)
        self.metadata = {'applicationId': 'com.ahmed9461.botos.app', 'variantName': 'ownerPreview',
                         'artifactType': {'type': 'APK'}, 'elements': [{'type': 'SINGLE', 'filters': [], 'outputFile': 'custom-name.apk'}]}
        self.save_metadata()
        self.apk = self.folder/'custom-name.apk'
        with zipfile.ZipFile(self.apk, 'w') as archive:
            for abi in ['arm64-v8a', 'x86_64']: archive.writestr(f'lib/{abi}/libtdjsonjava.so', b'synthetic library')
        self.evidence = self.outputs/'connected_additional_output/owner-startup.txt'
        self.evidence.parent.mkdir(parents=True)
        self.evidence.write_text('configured=true\napplicationId=com.ahmed9461.botos.app\ndebuggable=false\nconsentGate=true\nsessionCreated=false\naccountUsed=false\n')
        self.tools = self.root/'tools'; (self.tools/'lib').mkdir(parents=True)
        (self.tools/'lib/apksigner.jar').write_bytes(b'synthetic tool')
        self.destination = self.root/'private/payload.zip'
        self.update_report = self.root/'diagnostics/owner-update.json'
        self.update_report.parent.mkdir(parents=True)
        self.update_report.write_text(json.dumps({
            'application_id': 'com.ahmed9461.botos.app', 'in_place_reinstall': 'passed',
            'private_file_preserved': True, 'preferences_preserved': True,
            'uninstalled_between_installs': False, 'same_version_reinstall': True,
            'signer': 'temporary CI', 'owner_final_signer_device_test': False, 'account_used': False,
        }))
    def save_metadata(self, metadata=None):
        (self.folder/'output-metadata.json').write_text(json.dumps(self.metadata if metadata is None else metadata))
    def package(self, process=None):
        with patch.object(module, 'ROOT', self.root), patch.dict(module.os.environ,
                {'BOTOS_TESTED_SOURCE':'a'*40, 'BOTOS_INTEGRATION_RUN':'123', 'GITHUB_RUN_ID':'456'}), \
                patch.object(module.subprocess, 'run', side_effect=process) as run:
            module.package_owner(self.destination, self.tools)
            return run
    def test_nested_agp_layout_reproduces_original_bug_and_now_passes(self):
        self.assertEqual([], ET.parse(self.report).getroot().findall('testcase'))
        module.validate_startup_report(self.report)
    def test_flat_junit_report_is_also_supported(self):
        self.report.write_text(FLAT); module.validate_startup_report(self.report)
    def test_hidden_failure_error_or_skip_is_rejected(self):
        for tag in ['failure','error','skipped']:
            self.report.write_text(NESTED.replace(' />', f'><{tag}/></testcase>'))
            with self.subTest(tag=tag), self.assertRaises(ValueError): module.validate_startup_report(self.report)
    def test_wrong_test_empty_or_duplicate_case_is_rejected(self):
        for xml in [NESTED.replace('OwnerBuildSmokeTest','OtherTest'), NESTED.replace(CASE,''),
                    NESTED.replace(CASE,CASE+CASE), NESTED.replace('startupRequiresConsentBeforeCreatingAnySession','otherMethod')]:
            self.report.write_text(xml)
            with self.subTest(xml=xml), self.assertRaises(ValueError): module.validate_startup_report(self.report)
    def test_nested_inconsistent_counts_are_rejected(self):
        for xml in [NESTED.replace('<testsuite tests="1"','<testsuite tests="2"'), NESTED.replace('failures="0"','failures="1"'),
                    NESTED.replace('tests="1"','tests="0"'), '<other tests="1">'+CASE+'</other>']:
            self.report.write_text(xml)
            with self.subTest(xml=xml), self.assertRaises(ValueError): module.validate_startup_report(self.report)
    def test_metadata_not_a_guessed_filename_selects_apk(self):
        self.assertEqual(self.apk, module.locate_owner_apk(self.outputs))
    def test_wrong_identity_variant_or_artifact_type_is_rejected(self):
        for key,value in [('applicationId','other.app'),('variantName','debug'),('artifactType',{'type':'BUNDLE'})]:
            self.save_metadata(self.metadata | {key:value})
            with self.subTest(key=key), self.assertRaises(ValueError): module.locate_owner_apk(self.outputs)
    def test_path_traversal_absolute_backslash_and_missing_apk_are_rejected(self):
        for name in ['../custom-name.apk','/tmp/custom-name.apk','..\\custom-name.apk','missing.apk']:
            data=copy.deepcopy(self.metadata);data['elements'][0]['outputFile']=name;self.save_metadata(data)
            with self.subTest(name=name), self.assertRaises(ValueError): module.locate_owner_apk(self.outputs)
    def test_symlink_outside_output_folder_is_rejected(self):
        self.apk.unlink(); outside=self.root/'outside.apk';outside.write_bytes(b'synthetic');self.apk.symlink_to(outside)
        with self.assertRaises(ValueError): module.locate_owner_apk(self.outputs)
    def test_split_or_multiple_apks_are_rejected(self):
        for elements in [[], self.metadata['elements']*2, [{'type':'SINGLE','filters':[{'value':'x86_64'}],'outputFile':'custom-name.apk'}]]:
            self.save_metadata(self.metadata | {'elements':elements})
            with self.subTest(elements=elements), self.assertRaises(ValueError): module.locate_owner_apk(self.outputs)
    def test_package_allowlist_and_signature_alignment_calls(self):
        (self.folder/'BuildConfig.java').write_text('must not be archived')
        run=self.package()
        self.assertEqual(['apksigner','zipalign'],[Path(c.args[0][0]).name for c in run.call_args_list])
        with zipfile.ZipFile(self.destination) as archive:
            self.assertEqual({'BotOS-0.4.0-preview-ci.apk','tools/apksigner.jar','owner-startup.txt','owner-startup.xml','owner-update.json','provenance.json'},set(archive.namelist()))
            manifest=json.loads(archive.read('provenance.json'));self.assertFalse(manifest['account_used']);self.assertFalse(manifest['debuggable'])
    def test_configuration_conflict_and_extra_report_never_create_payload(self):
        self.evidence.write_text(self.evidence.read_text().replace('configured=true','configured=false'))
        with self.assertRaises(ValueError): self.package()
        self.assertFalse(self.destination.exists())
        (self.report.parent/'TEST-other.xml').write_text(NESTED)
        with self.assertRaises(ValueError): self.package()
        self.assertFalse(self.destination.exists())
    def test_failed_signature_never_creates_payload(self):
        with self.assertRaises(subprocess.CalledProcessError):
            self.package(process=subprocess.CalledProcessError(1,'synthetic verifier'))
        self.assertFalse(self.destination.exists())
    def test_malformed_report_is_not_treated_as_a_pass(self):
        self.report.write_text('<testsuites')
        with self.assertRaises(ET.ParseError): module.validate_startup_report(self.report)

    def test_missing_or_failed_update_evidence_blocks_package(self):
        self.update_report.write_text('{"in_place_reinstall":"failed"}')
        with self.assertRaises(ValueError): self.package()
        self.assertFalse(self.destination.exists())
        self.update_report.unlink()
        with self.assertRaises(FileNotFoundError): self.package()
        self.assertFalse(self.destination.exists())
