package com.ahmed9461.botos

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.ahmed9461.botos.design.BotOsTheme
import com.ahmed9461.botos.model.*
import com.ahmed9461.botos.telegram.runtime.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real system IME + the exact AppFrame and composer used in a real conversation; no demo route. */
@RunWith(AndroidJUnit4::class)
class MessengerImeTest {
    companion object { @JvmStatic @BeforeClass fun language() = setMessengerTestArabic() }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private data class WindowSnapshot(val focused: Boolean, val ime: Boolean, val height: Int, val bottom: Int, val density: Float)
    private fun window(): WindowSnapshot {
        var result: WindowSnapshot? = null
        ui.activityRule.scenario.onActivity { activity ->
            val root = activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(root)
            result = WindowSnapshot(root.hasWindowFocus(), insets?.isVisible(WindowInsetsCompat.Type.ime()) == true,
                root.height, insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0, activity.resources.displayMetrics.density)
        }
        return checkNotNull(result)
    }
    @Test fun actualConversationComposerTracksTheRealKeyboardWithoutADockGap() {
        ui.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        var draft by mutableStateOf("")
        var sends = 0
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BotOsTheme(ThemeMode.DARK, true) {
                    AppFrame(remember { SnackbarHostState() }, {}) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                            LiveBotPanel(ConversationState("fixture_bot", ConversationStatus.READY, MessageTimeline(ChatKey("fixture", "100"))),
                                draft, { draft = it }, { sends++; draft = "" }, {}, {}, {}, {}, {}, {}, {}, attachmentEnabled = true)
                        }
                    }
                }
            }
        }
        ui.waitUntil(10_000) { window().focused }
        ui.onNodeWithTag("live-input").performTouchInput { click() }
        ui.waitUntil(15_000) { window().ime }
        ui.onNodeWithTag("live-input").assertIsFocused().performTextInput("رسالة")
        ui.onNodeWithTag("bottom-dock").assertDoesNotExist()
        ui.waitForIdle()
        val view = window()
        val composer = ui.onNodeWithTag("composer-bar").fetchSemanticsNode().boundsInWindow
        val gapDp = (view.height - view.bottom - composer.bottom) / view.density
        PlatformTestStorageRegistry.getInstance().openOutputFile("keyboard-gap.txt").bufferedWriter().use {
            it.write("rootHeight=${view.height}\nimeBottom=${view.bottom}\ncomposerBottom=${composer.bottom}\ngapDp=$gapDp\n")
        }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("No device screenshot")
        try { PlatformTestStorageRegistry.getInstance().openOutputFile("keyboard-ar.png").use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
        assertTrue("Composer must not overlap or float far above the IME: $gapDp dp", gapDp >= -2f && gapDp <= 12f)
        ui.onNodeWithTag("live-send").performClick()
        ui.onNodeWithTag("live-input").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        ui.runOnIdle { assertEquals(1, sends) }
    }
}
