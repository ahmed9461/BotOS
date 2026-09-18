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
        self.assertNotIn("input keyevent 82", self.script)
        self.assertNotIn("KEYCODE_MENU", self.script)
        self.assertIn("wm dismiss-keyguard", self.script)

    def test_disposable_emulator_parks_on_settings_before_instrumentation(self):
        guard = self.script.index("ro.kernel.qemu")
        resolve_home = self.script.index("resolve-activity --brief --user 0")
        stop_home = self.script.index('am force-stop "$home_package"')
        settings = self.script.index("android.settings.SETTINGS")
        instrumentation = self.script.index(":app:connectedDebugAndroidTest")
        self.assertLess(guard, resolve_home)
        self.assertLess(resolve_home, stop_home)
        self.assertLess(stop_home, settings)
        self.assertLess(settings, instrumentation)
        self.assertIn("mResumedActivity", self.script)
        self.assertIn("com.android.settings", self.script)

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
