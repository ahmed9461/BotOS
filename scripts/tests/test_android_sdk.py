"""Isolated command/exit-status tests; these do NOT install or test Android."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "install_android_sdk.sh"
FAKE = '''#!/usr/bin/env bash
printf 'CALL:%s\\n' "$0" >> "$SDK_TEST_LOG"
printf 'ARG:%s\\n' "$@" >> "$SDK_TEST_LOG"
case " $* " in
  *' --version '*) printf 'test-tools-1\\n'; exit 0 ;;
  *' --licenses '*) read -r answer || true; exit "${SDK_LICENSE_EXIT:-0}" ;;
esac
exit "${SDK_INSTALL_EXIT:-0}"
'''

class SdkSetupTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.root = self.base / "sdk with spaces"
        self.root.mkdir()
        self.log = self.base / "calls"
        self.env = {k: v for k, v in os.environ.items()
                    if k not in {"ANDROID_HOME", "ANDROID_SDK_ROOT", "GITHUB_ENV", "GITHUB_PATH"}}
        self.env.update(ANDROID_HOME=str(self.root), SDK_TEST_LOG=str(self.log))

    def manager(self, version="latest"):
        path = self.root / "cmdline-tools" / version / "bin" / "sdkmanager"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(FAKE)
        path.chmod(0o755)
        return path

    def run_setup(self, *packages):
        return subprocess.run(["bash", str(SCRIPT), *(packages or ("platforms;android-37", "build-tools;36.0.0"))],
                              env=self.env, text=True, capture_output=True, timeout=10)

    def calls(self):
        return self.log.read_text() if self.log.exists() else ""

    def test_root_with_spaces_and_packages_remain_separate_arguments(self):
        self.manager()
        result = self.run_setup()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.calls().count("CALL:"), 3)
        self.assertIn(f"ARG:--sdk_root={self.root}\n", self.calls())
        self.assertIn("ARG:platforms;android-37\nARG:build-tools;36.0.0\n", self.calls())

    def test_versioned_tools_fallback_prefers_highest_installed_version(self):
        self.manager("9.0")
        chosen = self.manager("19.0")
        self.assertEqual(self.run_setup().returncode, 0)
        self.assertIn(f"CALL:{chosen}", self.calls())
        self.assertNotIn("/9.0/", self.calls())

    def test_sdk_root_environment_alias(self):
        self.manager()
        self.env["ANDROID_SDK_ROOT"] = self.env.pop("ANDROID_HOME")
        self.assertEqual(self.run_setup().returncode, 0)

    def test_missing_root_fails_clearly(self):
        self.env.pop("ANDROID_HOME")
        result = self.run_setup()
        self.assertEqual(result.returncode, 2)
        self.assertIn("installed SDK", result.stderr)
        self.assertEqual(self.calls(), "")

    def test_nonexistent_root_fails(self):
        self.env["ANDROID_HOME"] = str(self.base / "absent")
        self.assertEqual(self.run_setup().returncode, 2)

    def test_missing_tool_does_not_use_an_unrelated_path_tool(self):
        unrelated = self.base / "bin"
        unrelated.mkdir()
        tool = unrelated / "sdkmanager"
        tool.write_text(FAKE)
        tool.chmod(0o755)
        self.env["PATH"] = f"{unrelated}:{self.env['PATH']}"
        result = self.run_setup()
        self.assertEqual(result.returncode, 127)
        self.assertIn("No executable sdkmanager", result.stderr)
        self.assertEqual(self.calls(), "")

    def test_conflicting_sdk_roots_fail(self):
        self.manager()
        other = self.base / "other"
        other.mkdir()
        self.env["ANDROID_SDK_ROOT"] = str(other)
        self.assertEqual(self.run_setup().returncode, 2)
        self.assertEqual(self.calls(), "")

    def test_license_failure_is_not_hidden_and_install_is_not_started(self):
        self.manager()
        self.env["SDK_LICENSE_EXIT"] = "13"
        self.assertEqual(self.run_setup().returncode, 13)
        self.assertNotIn("ARG:platforms;", self.calls())

    def test_install_failure_is_propagated(self):
        self.manager()
        self.env["SDK_INSTALL_EXIT"] = "29"
        self.assertEqual(self.run_setup().returncode, 29)

    def test_exports_resolved_sdk_for_following_actions(self):
        self.manager()
        env_file, path_file = self.base / "env", self.base / "path"
        self.env.update(GITHUB_ENV=str(env_file), GITHUB_PATH=str(path_file))
        self.assertEqual(self.run_setup().returncode, 0)
        self.assertIn(f"ANDROID_HOME={self.root}\n", env_file.read_text())
        self.assertIn(str(self.root / "cmdline-tools/latest/bin"), path_file.read_text())

if __name__ == "__main__":
    unittest.main()
