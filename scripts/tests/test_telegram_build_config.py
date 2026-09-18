import contextlib
import hashlib
import importlib.util
import io
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

path = Path(__file__).resolve().parents[1] / "telegram_build_config.py"
spec = importlib.util.spec_from_file_location("telegram_build_config", path)
config = importlib.util.module_from_spec(spec)
spec.loader.exec_module(config)
# These values are deliberately synthetic and are never used with Telegram servers.
FIXTURE_HASH = hashlib.sha256(b"BotOS build configuration test fixture").hexdigest()[:32]
VALID = {config.ID_KEY: "12345", config.HASH_KEY: FIXTURE_HASH}


class TelegramBuildConfigurationTest(unittest.TestCase):
    def test_missing_pair_disables_preview(self):
        self.assertFalse(config.is_configured({}))

    def test_required_pair_cannot_be_missing(self):
        with self.assertRaises(ValueError): config.is_configured({}, required=True)

    def test_valid_pair(self):
        self.assertTrue(config.is_configured(VALID, required=True))

    def test_partial_pair_rejected(self):
        for key in VALID:
            with self.subTest(key=key), self.assertRaises(ValueError):
                config.is_configured({key: VALID[key]})

    def test_id_uses_positive_int32_and_ascii(self):
        for bad in ["0", "-1", "+1", "01", "2147483648", "12345678901", "١٢٣٤٥", "1e3", " 12", "12\n", "x"]:
            with self.subTest(value=bad), self.assertRaises(ValueError):
                config.is_configured(VALID | {config.ID_KEY: bad})
        self.assertTrue(config.is_configured(VALID | {config.ID_KEY: "2147483647"}))

    def test_hash_rejects_wrong_shape_and_source_injection(self):
        for bad in ["", "a" * 31, "a" * 33, "z" * 32, FIXTURE_HASH + "\n", '\";throw new Exception();', "\x00" * 32]:
            with self.subTest(value=bad), self.assertRaises(ValueError):
                config.is_configured(VALID | {config.HASH_KEY: bad})
        self.assertTrue(config.is_configured(VALID | {config.HASH_KEY: FIXTURE_HASH.upper()}))

    def write_generated(self, root, *, configured=True, api_id="12345", api_hash=FIXTURE_HASH):
        p = Path(root) / "BuildConfig.java"
        p.write_text("public final class BuildConfig {\n"
            + " public static final boolean TELEGRAM_CONFIGURED = " + str(configured).lower() + ";\n"
            + " public static final int TELEGRAM_API_ID = " + api_id + ";\n"
            + ' public static final String TELEGRAM_API_HASH = "' + api_hash + '";\n}\n')
        return p

    def test_generated_values_match_injected_values(self):
        with tempfile.TemporaryDirectory() as root:
            self.write_generated(root)
            config.verify_generated(Path(root), VALID, required=True)

    def test_generated_mismatch_is_redacted(self):
        with tempfile.TemporaryDirectory() as root:
            self.write_generated(root, api_hash="f" * 32)
            with self.assertRaises(ValueError) as caught:
                config.verify_generated(Path(root), VALID, required=True)
            self.assertNotIn(FIXTURE_HASH, str(caught.exception))
            self.assertNotIn("f" * 32, str(caught.exception))

    def test_disabled_generation(self):
        with tempfile.TemporaryDirectory() as root:
            self.write_generated(root, configured=False, api_id="0", api_hash="")
            config.verify_generated(Path(root), {})

    def test_duplicate_or_missing_generated_file_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError): config.verify_generated(Path(root), VALID)
            self.write_generated(root)
            other = Path(root) / "duplicate"; other.mkdir(); self.write_generated(other)
            with self.assertRaises(ValueError): config.verify_generated(Path(root), VALID)

    def test_cli_does_not_echo_secrets_on_success_or_failure(self):
        for values in [VALID, VALID | {config.HASH_KEY: FIXTURE_HASH + "x"}, {}]:
            stream = io.StringIO()
            with patch.dict(os.environ, values, clear=True), patch.object(sys, "argv", [str(path), "--require"]), contextlib.redirect_stdout(stream), contextlib.redirect_stderr(stream):
                code = config.main()
            self.assertEqual(code, 0 if values == VALID else 1)
            self.assertNotIn(FIXTURE_HASH, stream.getvalue())
            self.assertNotIn("12345", stream.getvalue())
            self.assertNotIn("Traceback", stream.getvalue())
