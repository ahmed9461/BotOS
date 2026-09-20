from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]

class PrelaunchLocaleTest(unittest.TestCase):
    def test_arabic_is_set_before_activity_launch_not_inside_live_setup(self):
        source = (ROOT / 'app/src/androidTest/kotlin/com/ahmed9461/botos/UiRegressionTest.kt').read_text()
        self.assertIn('import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule', source)
        self.assertIn('@JvmStatic @BeforeClass fun configureArabicBeforeActivityLaunch()', source)
        prelaunch, setup = source.split('@Before fun ready()', 1)
        self.assertIn('.applicationLocales = LocaleList.forLanguageTags("ar")', prelaunch)
        self.assertNotIn('.applicationLocales =', setup)
        self.assertIn('activity.resources.configuration.locales[0].language == "ar"', setup)
        self.assertIn('repeat(6)', setup)
        self.assertIn('ui.activityRule.scenario.recreate()', setup)
        self.assertIn('gapDp >= -2f && gapDp <= 12f', setup)
