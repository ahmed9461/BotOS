package com.ahmed9461.botos

import android.app.LocaleManager
import android.graphics.Bitmap
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.ahmed9461.botos.data.LocalAvatarStore
import com.ahmed9461.botos.data.StoreSnapshot
import com.ahmed9461.botos.design.BotOsTheme
import com.ahmed9461.botos.media.*
import com.ahmed9461.botos.model.*
import com.ahmed9461.botos.telegram.runtime.PendingReply
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AvatarStreamingUiTest {
    companion object {
        @JvmStatic @BeforeClass fun arabicBeforeActivityLaunch() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            if (Build.VERSION.SDK_INT >= 33) instrumentation.runOnMainSync {
                instrumentation.targetContext.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("ar")
            }
            instrumentation.waitForIdleSync()
        }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val bot = SavedBot("fixture", "fixture_bot", "مساعد تجريبي")
    private val chat = ChatKey("fixture-account", "fixture-chat")
    private fun draw(content: @Composable () -> Unit) {
        ui.setContent {
            BotOsTheme(ThemeMode.DARK, true) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground) {
                        Box(Modifier.systemBarsPadding()) { content() }
                    }
                }
            }
        }
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("Missing screenshot")
        try { PlatformTestStorageRegistry.getInstance().openOutputFile("$name.png").use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }
    @Test fun pictureOccupiesExistingLibraryHeaderAndSwitcherBadges() {
        val picture = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff6570a4.toInt()) }
        var library by mutableStateOf(true)
        draw {
            CompositionLocalProvider(LocalAvatarSnapshot provides AvatarSnapshot(mapOf(LocalAvatarStore.key(bot) to picture))) {
                if (library) LibraryScreen(StoreSnapshot(Workspace(listOf(bot))), {}, {})
                else WorkspaceScreen(StoreSnapshot(Workspace(listOf(bot))), bot.id, MessageTimeline(chat), "", false,
                    {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, liveContent = {})
            }
        }
        ui.onNodeWithTag("avatar-image-fixture", useUnmergedTree = true).assertIsDisplayed()
        ui.onNodeWithTag("avatar-letter-fixture", useUnmergedTree = true).assertDoesNotExist()
        screenshot("avatars-library-ar")
        ui.runOnIdle { library = false }
        ui.onNodeWithTag("avatar-image-fixture", useUnmergedTree = true).assertIsDisplayed()
        ui.onNodeWithTag("bot-switcher").performClick()
        ui.onNodeWithTag("bot-switch-item-fixture").assertIsDisplayed()
        assertEquals(2, ui.onAllNodesWithTag("avatar-image-fixture", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
    @Test fun customPictureOptionsCanReturnToAutomaticWithoutEditingBookmark() {
        var custom by mutableStateOf(true)
        var resets = 0
        var picks = 0
        draw { AvatarOptions(bot, custom, false, {}, { picks++ }, { custom = false; resets++ }) }
        ui.onNodeWithTag("avatar-choose").assertIsEnabled().performClick()
        ui.onNodeWithTag("avatar-reset").performClick()
        ui.onNodeWithTag("avatar-reset").assertDoesNotExist()
        ui.runOnIdle { assertEquals(1, picks); assertEquals(1, resets) }
    }
    @Test fun pendingReplyUpdatesInPlaceAndFinalMessageReplacesIt() {
        val initial = BotMessage(Long.MIN_VALUE, chat, 1, listOf(Block.Paragraph("stream", "بداية الرد")))
        var pending by mutableStateOf<PendingReply?>(PendingReply(77, initial, true, false, Long.MAX_VALUE))
        var timeline by mutableStateOf(MessageTimeline(chat))
        var stopped: Long? = null
        draw { MessageTimelineView(timeline, {}, pending = pending, onStopPending = { stopped = it }) }
        ui.onNodeWithText("بداية الرد").assertIsDisplayed()
        ui.onNodeWithTag("pending-stop").performClick()
        ui.runOnIdle {
            assertEquals(77L, stopped)
            pending = pending!!.copy(content = initial.copy(revision = 2, blocks = listOf(Block.Paragraph("stream", "بداية الرد ثم تفاصيل جديدة"))))
        }
        ui.onNodeWithText("بداية الرد").assertDoesNotExist()
        ui.onNodeWithText("بداية الرد ثم تفاصيل جديدة").assertIsDisplayed()
        assertEquals(1, ui.onAllNodesWithTag("pending-reply").fetchSemanticsNodes().size)
        ui.onNodeWithTag("delivery-${Long.MIN_VALUE}").assertDoesNotExist()
        screenshot("streaming-reply-ar")
        ui.runOnIdle {
            pending = null
            timeline = MessageTimeline(chat, listOf(initial.copy(id = 91, date = 1_789_000_000, blocks = listOf(Block.Paragraph("final", "الرد النهائي")))))
        }
        ui.onNodeWithTag("pending-reply").assertDoesNotExist()
        ui.onNodeWithText("الرد النهائي").assertIsDisplayed()
    }
    @Test fun foreignPendingReplyAndStopActionNeverAppearInAnotherConversation() {
        val draft = PendingReply(77, BotMessage(Long.MIN_VALUE, ChatKey("other", "other"), 1,
            listOf(Block.Paragraph("stream", "لا يظهر هنا"))), true, false, Long.MAX_VALUE)
        draw { MessageTimelineView(MessageTimeline(chat), {}, pending = draft) }
        ui.onNodeWithTag("pending-reply").assertDoesNotExist()
        ui.onNodeWithTag("pending-stop").assertDoesNotExist()
        ui.onNodeWithText("لا يظهر هنا").assertDoesNotExist()
    }
}
