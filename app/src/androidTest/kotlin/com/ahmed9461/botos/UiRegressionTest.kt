package com.ahmed9461.botos

import android.app.LocaleManager
import android.graphics.Bitmap
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real activity, real DataStore and IME. No mocked preference response or fixed keyboard height. */
@RunWith(AndroidJUnit4::class)
class UiRegressionTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Before fun ready() {
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("bottom-dock").fetchSemanticsNodes().isNotEmpty() }
        if (Build.VERSION.SDK_INT >= 33) {
            ui.activityRule.scenario.onActivity {
                it.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("ar")
            }
        }
        ui.waitForIdle()
    }

    private fun enabled(tag: String) {
        ui.waitUntil(10_000) {
            ui.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.config?.contains(SemanticsProperties.Disabled) == false
        }
    }
    private fun motion(): ToggleableState = ui.onNodeWithTag("motion-toggle").fetchSemanticsNode().config[SemanticsProperties.ToggleableState]
    private fun theme(name: String) {
        enabled("theme-$name")
        ui.onNodeWithTag("theme-$name").performClick()
        ui.waitUntil(10_000) {
            ui.onNodeWithTag("theme-$name").fetchSemanticsNode().config[SemanticsProperties.Selected]
        }
        enabled("theme-$name")
        ui.onNodeWithTag("theme-$name").assertIsSelected()
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("Device screenshot unavailable")
        val directory = File(ui.activity.getExternalFilesDir(null), "ui-evidence").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun a_preferencesUpdateInPlaceAndSurviveRecreation() {
        ui.onNodeWithTag("nav-appearance").performClick()
        enabled("motion-toggle")
        val before = motion()
        repeat(6) {
            enabled("motion-toggle")
            val target = if (motion() == ToggleableState.On) ToggleableState.Off else ToggleableState.On
            ui.onNodeWithTag("motion-toggle").performClick()
            ui.waitUntil(10_000) { motion() == target }
            ui.onNodeWithTag("nav-appearance").assertIsSelected()
        }
        assertTrue("An even number of real writes restores the initial value", motion() == before)
        theme("LIGHT")
        screenshot("appearance-light-ar")
        theme("DARK")
        screenshot("appearance-dark-ar")
        enabled("motion-toggle")
        val expected = if (motion() == ToggleableState.On) ToggleableState.Off else ToggleableState.On
        ui.onNodeWithTag("motion-toggle").performClick()
        ui.waitUntil(10_000) { motion() == expected }
        enabled("motion-toggle")
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.waitUntil(10_000) { motion() == expected }
        ui.onNodeWithTag("theme-DARK").assertIsSelected()
        screenshot("appearance-restored-ar")
    }

    @Test fun b_composerTracksRealImeWithoutAnEmptyDock() {
        ui.onNodeWithTag("nav-workspace").performClick()
        enabled("preview-tab")
        ui.onNodeWithTag("preview-tab").performClick()
        screenshot("workspace-ar")
        ui.onNodeWithTag("composer-input").performClick().performTextInput("رسالة تجريبية")
        ui.waitUntil(15_000) {
            ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        ui.onNodeWithTag("bottom-dock").assertDoesNotExist()
        ui.waitForIdle()
        val root = ui.activity.window.decorView
        val inset = ViewCompat.getRootWindowInsets(root)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val composer = ui.onNodeWithTag("composer-bar").fetchSemanticsNode().boundsInWindow
        val gap = root.height - inset - composer.bottom
        val density = ui.activity.resources.displayMetrics.density
        val gapDp = gap / density
        val directory = File(ui.activity.getExternalFilesDir(null), "ui-evidence").apply { mkdirs() }
        File(directory, "keyboard-gap.txt").writeText("rootHeight=${root.height}\nimeBottom=$inset\ncomposerBottom=${composer.bottom}\ngapDp=$gapDp\n")
        screenshot("keyboard-ar")
        assertTrue("Composer overlaps IME or leaves an excessive gap: $gapDp dp", gapDp >= -2f && gapDp <= 12f)
        ui.onNodeWithTag("send-preview").performClick()
        ui.onNodeWithTag("composer-input").assertTextEquals("")
    }

    @Test fun c_libraryAndEditorNavigationPreserveInputOnRecreation() {
        ui.onNodeWithTag("nav-library").performClick()
        screenshot("library-ar")
        ui.onNodeWithTag("nav-workspace").performClick()
        enabled("add-bot")
        ui.onNodeWithTag("add-bot").performClick()
        ui.onNodeWithTag("bot-username").performTextInput("@example_bot")
        ui.onNodeWithTag("bot-title").performTextInput("مكتبتي")
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithTag("bot-username").assertTextContains("@example_bot")
        ui.onNodeWithTag("bot-title").assertTextContains("مكتبتي")
        screenshot("editor-ar")
        ui.onNodeWithTag("editor-back").performClick()
        ui.onNodeWithTag("workspace-screen").assertIsDisplayed()
    }
}
