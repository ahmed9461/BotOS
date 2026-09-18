package com.ahmed9461.botos

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahmed9461.botos.design.BotOsTheme
import com.ahmed9461.botos.model.ThemeMode
import com.ahmed9461.botos.telegram.runtime.AccountState
import com.ahmed9461.botos.telegram.runtime.AuthStep
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Presentation contracts only. These fixtures do not connect to Telegram or use real credentials. */
@RunWith(AndroidJUnit4::class)
class AccountScreenTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val state = mutableStateOf(AccountState(configured = false))
    private var connectionAccepted = false
    private var submissions = 0
    private var submittedStep: AuthStep? = null
    private var logoutCount = 0

    private fun content(restoration: StateRestorationTester? = null) {
        val render: @androidx.compose.runtime.Composable () -> Unit = {
            BotOsTheme(ThemeMode.LIGHT, true) {
                Box(Modifier.fillMaxSize()) {
                    AccountScreen(state.value, {}, { connectionAccepted = it }, { step, _ ->
                        submissions++; submittedStep = step
                    }, {}, {}, { logoutCount++ })
                }
            }
        }
        if (restoration == null) ui.setContent(render) else restoration.setContent(render)
    }
    private fun assertSecure(expected: Boolean) {
        ui.waitForIdle()
        var actual = false
        ui.activityRule.scenario.onActivity {
            actual = it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        }
        assertEquals(expected, actual)
    }
    private fun emptyInput() = ui.onNodeWithTag("account-input").assert(
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))

    @Test fun unavailableBuildNeverCollectsCredentials() {
        content()
        ui.onNodeWithTag("account-unavailable").assertIsDisplayed()
        ui.onNodeWithTag("account-input").assertDoesNotExist()
        ui.onNodeWithTag("account-connect").assertDoesNotExist()
        assertSecure(false)
    }

    @Test fun consentAndAuthoritativeStepsControlSignIn() {
        state.value = AccountState(configured = true)
        content()
        ui.onNodeWithTag("account-connect").assertIsNotEnabled()
        ui.onNodeWithTag("account-consent").performScrollTo().performClick()
        ui.onNodeWithTag("account-connect").performScrollTo().assertIsEnabled().performClick()
        ui.runOnIdle { assertTrue(connectionAccepted) }
        // The connect callback cannot invent a phone challenge before a state update arrives.
        ui.onNodeWithTag("account-input").assertDoesNotExist()
        ui.runOnIdle { state.value = AccountState(true, AuthStep.PHONE) }
        assertSecure(true)
        ui.onNodeWithTag("account-input").performScrollTo().performTextInput("+15550001234")
        ui.onNodeWithTag("account-submit").performScrollTo().performClick()
        emptyInput()
        ui.runOnIdle { assertEquals(1, submissions); assertEquals(AuthStep.PHONE, submittedStep) }
        ui.runOnIdle { state.value = AccountState(true, AuthStep.CODE) }
        emptyInput()
    }

    @Test fun transientInputDoesNotRestoreAndSecureFlagIsScoped() {
        state.value = AccountState(true, AuthStep.PASSWORD)
        val restoration = StateRestorationTester(ui)
        content(restoration)
        assertSecure(true)
        ui.onNodeWithTag("account-input").performScrollTo().performTextInput("fixture-only-never-a-real-secret")
        restoration.emulateSavedInstanceStateRestore()
        emptyInput()
        assertSecure(true)
        ui.runOnIdle { state.value = AccountState(true, AuthStep.CLOSED) }
        ui.onNodeWithTag("account-input").assertDoesNotExist()
        assertSecure(false)
        // A host's pre-existing security flag must never be cleared by this screen.
        ui.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        ui.runOnIdle { state.value = AccountState(true, AuthStep.CODE) }
        assertSecure(true)
        ui.runOnIdle { state.value = AccountState(true, AuthStep.CLOSED) }
        assertSecure(true)
    }

    @Test fun logoutRequiresASecondExplicitConfirmation() {
        state.value = AccountState(true, AuthStep.READY, displayName = "Test account")
        content()
        ui.onNodeWithTag("account-logout").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(0, logoutCount) }
        ui.onNodeWithTag("account-confirm-logout").performClick()
        ui.runOnIdle { assertEquals(1, logoutCount) }
    }
}
