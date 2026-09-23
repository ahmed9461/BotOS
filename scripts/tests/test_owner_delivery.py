import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('owner_delivery', Path(__file__).resolve().parents[1] / 'owner_delivery.py')
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)


class OwnerRequestTest(unittest.TestCase):
    def setUp(self):
        self.request = {'enabled': True, 'source_commit': 'a' * 40, 'integration_run': 123}
        self.run = {'id': 123, 'head_sha': 'a' * 40, 'status': 'completed', 'conclusion': 'success',
                    'event': 'pull_request', 'path': '.github/workflows/android.yml',
                    'head_repository': {'full_name': 'ahmed9461/BotOS'}, 'head_branch': 'feat/media-profiles-streaming', 'run_number': 32}
    def test_disabled_request_never_needs_credentials(self):
        self.assertEqual({'enabled': 'false'}, module.validate_request({'enabled': False}, {}, []))
    def test_exact_success_and_docs_only_changes_are_accepted(self):
        self.assertEqual('true', module.validate_request(self.request, self.run, ['delivery/request.json', 'docs/TESTING.md'])['enabled'])
    def test_wrong_run_or_source_or_failure_is_rejected(self):
        for key, value in [('head_sha', 'b'*40), ('conclusion', 'failure'), ('status', 'in_progress'), ('event', 'push'), ('id', 456), ('run_number', 0)]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                module.validate_request(self.request, self.run | {key: value}, [])
    def test_foreign_repository_or_workflow_is_rejected(self):
        for key, value in [('head_repository', {'full_name': 'other/BotOS'}), ('path', 'other.yml'),
                           ('head_branch', 'untrusted'), ('head_branch', 'feat/rich-chat-polish')]:
            with self.subTest(key=key), self.assertRaises(ValueError): module.validate_request(self.request, self.run | {key: value}, [])
    def test_any_untested_code_or_recipient_change_is_rejected(self):
        for path in ['app/build.gradle.kts', 'scripts/owner_delivery.py', 'delivery/recipient.pem', '.github/workflows/owner-delivery.yml']:
            with self.subTest(path=path), self.assertRaises(ValueError): module.validate_request(self.request, self.run, [path])
    def test_malformed_requests_are_rejected(self):
        for request in [{'enabled': 'true'}, self.request | {'integration_run': True}, self.request | {'source_commit': 'main'}, self.request | {'source_commit': 'a'*39}]:
            with self.subTest(request=request), self.assertRaises(ValueError): module.validate_request(request, self.run, [])


class SealedDeliveryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(); cls.root = Path(cls.temp.name)
        for name in ['recipient', 'other']:
            subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-sha256', '-days', '1',
                            '-subj', '/CN=Synthetic delivery test', '-keyout', str(cls.root/f'{name}.key'), '-out', str(cls.root/f'{name}.pem')],
                           check=True, capture_output=True)
    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.path=Path(self.tmp.name)
        self.addCleanup(self.tmp.cleanup)
        self.source=self.path/'synthetic.bin'; self.source.write_bytes(b'not a user secret\x00'*200)
        self.output=self.path/'sealed.cms'
    def decrypt(self, key='recipient', payload=None):
        return subprocess.run(['openssl','cms','-decrypt','-binary','-inform','DER','-in',str(payload or self.output),
                               '-inkey',str(self.root/f'{key}.key'),'-recip',str(self.root/f'{key}.pem')], capture_output=True)
    def test_authenticated_roundtrip_preserves_binary_and_not_plaintext(self):
        module.seal(self.source,self.root/'recipient.pem',self.output)
        result=self.decrypt(); self.assertEqual(0,result.returncode); self.assertEqual(self.source.read_bytes(),result.stdout)
        self.assertNotIn(b'not a user secret', self.output.read_bytes())
    def test_tampering_and_wrong_private_key_are_rejected(self):
        module.seal(self.source,self.root/'recipient.pem',self.output)
        self.assertNotEqual(0,self.decrypt(key='other').returncode)
        data=bytearray(self.output.read_bytes()); data[len(data)//2]^=1
        corrupted=self.path/'corrupted.cms';corrupted.write_bytes(data)
        self.assertNotEqual(0,self.decrypt(payload=corrupted).returncode)
    def test_missing_payload_invalid_recipient_and_overwrite_fail_closed(self):
        with self.assertRaises(ValueError): module.seal(self.path/'missing',self.root/'recipient.pem',self.output)
        with self.assertRaises(ValueError): module.seal(self.source,self.root/'recipient.key',self.output)
        self.output.write_bytes(b'keep')
        with self.assertRaises(ValueError): module.seal(self.source,self.root/'recipient.pem',self.output)
        self.assertEqual(b'keep',self.output.read_bytes())
    def test_openssl_failure_never_publishes_output(self):
        bad=self.path/'bad.pem'; bad.write_text('not a certificate')
        with self.assertRaises(subprocess.CalledProcessError): module.seal(self.source,bad,self.output)
        self.assertFalse(self.output.exists()); self.assertEqual([],list(self.path.glob('.sealed-*')))
