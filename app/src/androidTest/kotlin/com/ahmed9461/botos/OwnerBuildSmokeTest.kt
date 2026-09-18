package com.ahmed9461.botos

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.ahmed9461.botos.telegram.runtime.AuthStep
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real application startup only: never presses connect or uses an account, phone, or OTP. */
@RunWith(AndroidJUnit4::class)
class OwnerBuildSmokeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun startupRequiresConsentBeforeCreatingAnySession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val requireConfigured = InstrumentationRegistry.getArguments().getString("botos.requireConfigured") == "true"
        assertEquals("Unexpected build configuration mode", requireConfigured, BuildConfig.TELEGRAM_CONFIGURED)
        if (requireConfigured) {
            assertEquals("com.ahmed9461.botos.preview", context.packageName)
            assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        }
        // This smoke validates fresh startup, not a configuration change midway through it.
        // Locale, RTL and activity recreation remain covered by UiRegressionTest.
        Log.i("BotOSOwnerTest", "waiting_for_workspace")
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("bottom-dock").fetchSemanticsNodes().isNotEmpty() }
        Log.i("BotOSOwnerTest", "opening_appearance")
        ui.onNodeWithTag("nav-appearance").performClick()
        Log.i("BotOSOwnerTest", "opening_account")
        ui.onNodeWithTag("open-account").performScrollTo().performClick()
        Log.i("BotOSOwnerTest", "checking_consent_gate")
        ui.onNodeWithTag("account-input").assertDoesNotExist()
        if (requireConfigured) {
            ui.onNodeWithTag("account-consent").assertExists().assertIsOff()
            ui.onNodeWithTag("account-connect").assertExists().assertIsNotEnabled()
            ui.onNodeWithTag("account-unavailable").assertDoesNotExist()
        } else {
            ui.onNodeWithTag("account-unavailable").assertIsDisplayed()
            ui.onNodeWithTag("account-consent").assertDoesNotExist()
        }
        val app = context.applicationContext as BotOsApplication
        assertEquals(AuthStep.CLOSED, app.account.state.value.step)
        assertFalse("A fresh install must not create a session", File(context.noBackupFilesDir, "telegram").exists())
        Log.i("BotOSOwnerTest", "writing_startup_evidence")
        PlatformTestStorageRegistry.getInstance().openOutputFile("owner-startup.txt").bufferedWriter().use {
            it.write("configured=${BuildConfig.TELEGRAM_CONFIGURED}\napplicationId=${context.packageName}\n")
            it.write("debuggable=${context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0}\n")
            it.write("consentGate=true\nsessionCreated=false\naccountUsed=false\n")
        }
        Log.i("BotOSOwnerTest", "startup_verified")
    }
}
