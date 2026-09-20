package com.ahmed9461.botos

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.ahmed9461.botos.data.StoreSnapshot
import com.ahmed9461.botos.design.BotOsTheme
import com.ahmed9461.botos.model.*
import com.ahmed9461.botos.telegram.runtime.ConversationState
import com.ahmed9461.botos.telegram.runtime.ConversationStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic protocol-shaped content, never a user's private chat. */
@RunWith(AndroidJUnit4::class)
class RichChatRegressionTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val chat = ChatKey("fixture-account", "fixture-chat")
    private fun fixture() = MessageTimeline(chat, listOf(
        BotMessage(90, chat, 1, listOf(Block.Paragraph("command", "/start")), true, DeliveryState.SENT, date = 1_789_000_000),
        BotMessage(91, chat, 1, listOf(
            Block.Heading("title", StyledText(listOf(StyledSpan("مساعد تجريبي", setOf(RichMark.BOLD))))),
            Block.Paragraph("body", "مساعدك الشخصي. أرسل رسالتك وابدأ المحادثة."),
            Block.RichDetails("details", StyledText.plain("إظهار التفاصيل"), listOf(Block.Paragraph("detail", "التفاصيل التجريبية"))),
            Block.Paragraph("spoiler", StyledText(listOf(StyledSpan("نص مخفي تجريبي", setOf(RichMark.SPOILER))))),
            Block.Buttons("primary", listOf(BotButton("settings", "إعدادات المساعد", ActionPayload.Callback("AQID"), style = ButtonVisualStyle.PRIMARY))),
            Block.Buttons("paired", listOf(
                BotButton("help", "طريقة الاستخدام", ActionPayload.Callback("BAUG")),
                BotButton("new", "محادثة جديدة", ActionPayload.Callback("BwgJ"), style = ButtonVisualStyle.SUCCESS),
            )),
            Block.Buttons("delete", listOf(BotButton("delete", "حذف تجريبي", ActionPayload.Callback("Cg=="), style = ButtonVisualStyle.DANGER))),
        ), date = 1_789_000_003, forceRtl = true),
    ))
    private fun render(theme: ThemeMode) {
        val bot = SavedBot("fixture", "fixture_bot", "مساعد تجريبي")
        ui.setContent {
            BotOsTheme(theme, true) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        WorkspaceScreen(StoreSnapshot(Workspace(listOf(bot))), "fixture", fixture(), "", false,
                            {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, liveContent = {
                                LiveBotPanel(ConversationState("fixture_bot", ConversationStatus.READY, fixture()),
                                    "", {}, {}, {}, {}, {}, {}, {}, {}, {})
                            })
                    }
                }
            }
        }
        ui.waitForIdle()
    }
    private fun capture(name: String) {
        ui.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("Device screenshot unavailable")
        try { PlatformTestStorageRegistry.getInstance().openOutputFile("$name.png").use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }
    @Test fun lightChatHasExternalButtonsAndPreservesPhysicalRowOrderInArabic() {
        render(ThemeMode.LIGHT)
        val bubble = ui.onNodeWithTag("message-bubble-91").fetchSemanticsNode().boundsInRoot
        val row = ui.onNodeWithTag("message-buttons-91-primary").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val help = ui.onNodeWithText("طريقة الاستخدام").fetchSemanticsNode().boundsInRoot
        val fresh = ui.onNodeWithText("محادثة جديدة").fetchSemanticsNode().boundsInRoot
        assertTrue(row.top >= bubble.bottom - 1f)
        assertTrue("RTL must not reverse Telegram button order", help.left < fresh.left)
        ui.onNodeWithTag("compact-bot-header").assertIsDisplayed()
        capture("rich-chat-light-ar")
    }
    @Test fun darkChatAndSwitcherHaveRealScreenshotEvidence() {
        render(ThemeMode.DARK)
        capture("rich-chat-dark-ar")
        ui.onNodeWithTag("bot-switcher").performClick()
        ui.onNodeWithTag("bot-switch-item-fixture").assertIsDisplayed()
        capture("rich-chat-switcher-ar")
    }
    @Test fun detailsExpandAndSpoilersStayHiddenUntilExplicitReveal() {
        render(ThemeMode.LIGHT)
        ui.onNodeWithText("التفاصيل التجريبية").assertDoesNotExist()
        ui.onNodeWithText("إظهار التفاصيل").performClick()
        ui.onNodeWithText("التفاصيل التجريبية").assertIsDisplayed()
        ui.onNodeWithText("نص مخفي تجريبي").assertDoesNotExist()
        ui.onNodeWithTag("rich-text-spoiler").performTouchInput { click() }
        ui.onNodeWithText("نص مخفي تجريبي").assertIsDisplayed()
    }
}
