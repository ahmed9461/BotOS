package com.ahmed9461.botos

import android.app.KeyguardManager
import android.app.LocaleManager
import android.graphics.Bitmap
import android.os.Build
import android.os.LocaleList
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
        ui.activityRule.scenario.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val keyguard = activity.getSystemService(KeyguardManager::class.java)
            check(!keyguard.isDeviceSecure) { "UI tests require a disposable non-secure emulator" }
            if (keyguard.isKeyguardLocked) keyguard.requestDismissKeyguard(activity, null)
        }
        windowReady()
        Log.i("BotOSUiTest", "ready")
    }

    private data class WindowSnapshot(
        val focused: Boolean, val imeVisible: Boolean,
        val rootHeight: Int, val imeBottom: Int, val density: Float,
    )
    private fun windowSnapshot(): WindowSnapshot {
        var snapshot: WindowSnapshot? = null
        // Call from the instrumentation thread. Never nest ActivityScenario in runOnIdle:
        // the activity getter drains the main looper and can deadlock synchronization.
        ui.activityRule.scenario.onActivity { activity ->
            val root = activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(root)
            snapshot = WindowSnapshot(
                root.hasWindowFocus(),
                insets?.isVisible(WindowInsetsCompat.Type.ime()) == true,
                root.height, insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0,
                activity.resources.displayMetrics.density,
            )
        }
        return checkNotNull(snapshot)
    }
    private fun windowReady() {
        Log.i("BotOSUiTest", "waiting_for_window_focus")
        try {
            ui.waitUntil(10_000) { windowSnapshot().focused }
        } catch (failure: ComposeTimeoutException) {
            try { screenshot("window-focus-timeout") }
            catch (captureFailure: Exception) { failure.addSuppressed(captureFailure) }
            throw failure
        }
        Log.i("BotOSUiTest", "window_focused")
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
        Log.i("BotOSUiTest", "screenshot:$name")
        ui.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("Device screenshot unavailable")
        try {
            // The Gradle test runner retrieves this before uninstalling the tested application.
            PlatformTestStorageRegistry.getInstance().openOutputFile("$name.png").use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Screenshot compression failed" }
            }
        } finally { bitmap.recycle() }
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
        ui.onNodeWithTag("preview-mode").assertIsDisplayed()
        ui.onNodeWithTag("bot-switcher").assertIsDisplayed()
        screenshot("workspace-ar")
        windowReady()
        // Exercise the real pointer/focus path. Do not inject text before the input session exists.
        ui.onNodeWithTag("composer-input").assertIsDisplayed().performTouchInput { click() }
        try {
            ui.waitUntil(15_000) { windowSnapshot().imeVisible }
        } catch (failure: ComposeTimeoutException) {
            try { screenshot("keyboard-not-shown") }
            catch (captureFailure: Exception) { failure.addSuppressed(captureFailure) }
            throw failure
        }
        ui.onNodeWithTag("composer-input").assertIsFocused().performTextInput("رسالة تجريبية")
        ui.onNodeWithTag("bottom-dock").assertDoesNotExist()
        ui.waitForIdle()
        val window = windowSnapshot()
        val composer = ui.onNodeWithTag("composer-bar").fetchSemanticsNode().boundsInWindow
        val gap = window.rootHeight - window.imeBottom - composer.bottom
        val gapDp = gap / window.density
        PlatformTestStorageRegistry.getInstance().openOutputFile("keyboard-gap.txt").bufferedWriter().use {
            it.write("rootHeight=${window.rootHeight}\nimeBottom=${window.imeBottom}\ncomposerBottom=${composer.bottom}\ngapDp=$gapDp\n")
        }
        screenshot("keyboard-ar")
        assertTrue("Composer overlaps IME or leaves an excessive gap: $gapDp dp", gapDp >= -2f && gapDp <= 12f)
        ui.onNodeWithTag("send-preview").performClick()
        // The placeholder remains visible after a successful clear; inspect editable content only.
        ui.onNodeWithTag("composer-input").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
        )
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
    @Test fun d_unconfiguredAccountRouteDoesNotAskForCredentials() {
        ui.onNodeWithTag("nav-appearance").performClick()
        ui.onNodeWithTag("open-account").performScrollTo().performClick()
        ui.onNodeWithTag("account-unavailable").assertIsDisplayed()
        ui.onNodeWithTag("account-input").assertDoesNotExist()
        screenshot("account-unconfigured-ar")
        ui.onNodeWithTag("account-back").performClick()
        ui.onNodeWithTag("nav-appearance").assertIsSelected()
    }

}
