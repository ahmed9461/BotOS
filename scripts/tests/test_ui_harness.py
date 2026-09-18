from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class UiHarnessContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.script = (ROOT / "scripts/run_ui_tests.sh").read_text()
        cls.ui_test = (
            ROOT
            / "app/src/androidTest/kotlin/com/ahmed9461/botos/UiRegressionTest.kt"
        ).read_text()

    def test_menu_key_is_not_used_to_unlock_or_prepare_home(self):
        self.assertNotIn("shell input keyevent 82", self.script)
        self.assertNotIn("shell input keyevent KEYCODE_MENU", self.script)
        self.assertIn("shell input keyevent KEYCODE_WAKEUP", self.script)
        self.assertIn("wm dismiss-keyguard", self.script)

    def test_disposable_emulator_only_observes_stable_foreground(self):
        guard = self.script.index("ro.kernel.qemu")
        preflight = self.script.index("preflight_deadline")
        instrumentation = self.script.index(":app:connectedDebugAndroidTest")
        self.assertLess(guard, preflight)
        self.assertLess(preflight, instrumentation)
        self.assertNotIn("resolve-activity --brief --user 0", self.script)
        self.assertNotIn("am force-stop", self.script)
        self.assertNotIn("android.settings.SETTINGS", self.script)
        self.assertIn("dumpsys window lastanr", self.script)
        self.assertIn("<no ANR has occurred since boot>", self.script)
        self.assertIn("topResumedActivity=|ResumedActivity:", self.script)
        self.assertIn("mCurrentFocus=", self.script)

    def test_production_ui_regression_thresholds_are_not_relaxed(self):
        self.assertIn(
            "ui.waitUntil(10_000) { windowSnapshot().focused }", self.ui_test
        )
        self.assertIn(
            "gapDp >= -2f && gapDp <= 12f", self.ui_test
        )
        self.assertIn(
            "ui.waitUntil(15_000) { windowSnapshot().imeVisible }", self.ui_test
        )


if __name__ == "__main__":
    unittest.main()
