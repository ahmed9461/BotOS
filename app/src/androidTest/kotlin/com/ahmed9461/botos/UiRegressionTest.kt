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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real activity, real DataStore and IME. No mocked preference response or fixed keyboard height. */
@RunWith(AndroidJUnit4::class)
class UiRegressionTest {
    companion object {
        @JvmStatic @BeforeClass fun configureArabicBeforeActivityLaunch() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            if (Build.VERSION.SDK_INT >= 33) {
                instrumentation.runOnMainSync {
                    instrumentation.targetContext.getSystemService(LocaleManager::class.java)
                        .applicationLocales = LocaleList.forLanguageTags("ar")
                }
                instrumentation.waitForIdleSync()
            }
        }
    }
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Before fun ready() {
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("bottom-dock").fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
        ui.activityRule.scenario.onActivity { activity ->
            if (Build.VERSION.SDK_INT >= 33) assertTrue("Arabic is configured before activity launch", activity.resources.configuration.locales[0].language == "ar")
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

    @Test fun b_homeIsTheRealChatListWithoutPreviewOrSyntheticMessages() {
        ui.onNodeWithTag("nav-workspace").performClick()
        ui.onNodeWithTag("chat-list").assertIsDisplayed()
        ui.onNodeWithTag("preview-mode").assertDoesNotExist()
        ui.onNodeWithTag("message-list").assertDoesNotExist()
        ui.onNodeWithTag("add-bot").assertIsDisplayed()
        ui.onNodeWithTag("bottom-dock").assertIsDisplayed()
        screenshot("workspace-ar")
    }

    @Test fun c_libraryAndEditorNavigationPreserveInputOnRecreation() {
        Log.i("BotOSUiTest", "opening_library")
        ui.onNodeWithTag("nav-library").performClick()
        Log.i("BotOSUiTest", "library_opened")
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
    @Test fun e_savedBotOpensARealConnectionGateAndBackRestoresTheList() {
        val removeLabel = ui.activity.getString(R.string.remove)
        ui.onNodeWithTag("nav-workspace").performClick()
        enabled("add-bot")
        ui.onNodeWithTag("add-bot").performClick()
        ui.onNodeWithTag("bot-username").performTextInput("botos_fixture_bot")
        ui.onNodeWithTag("bot-title").performTextInput("بوت الاختبار")
        ui.onNodeWithTag("save-bot").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("live-connect-account").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("bottom-dock").assertDoesNotExist()
        ui.onNodeWithTag("live-input").assertDoesNotExist()
        ui.onNodeWithTag("chat-back").performClick()
        ui.onNodeWithTag("bottom-dock").assertIsDisplayed()
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("@botos_fixture_bot").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("@botos_fixture_bot").performClick()
        ui.onNodeWithTag("bot-actions").performClick()
        ui.onAllNodesWithText(removeLabel).filter(hasClickAction()).onFirst().performClick()
        ui.onAllNodesWithText(removeLabel).filter(hasClickAction()).onFirst().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("@botos_fixture_bot").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag("chat-list").assertIsDisplayed()
        ui.onNodeWithTag("bottom-dock").assertIsDisplayed()
    }
}
