package com.ahmed9461.botos

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahmed9461.botos.data.StoreSnapshot
import com.ahmed9461.botos.design.BotOsTheme
import com.ahmed9461.botos.model.*
import com.ahmed9461.botos.telegram.runtime.ConversationState
import com.ahmed9461.botos.telegram.runtime.ConversationStatus
import com.ahmed9461.botos.telegram.runtime.AttachmentKind
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI fixtures only. No native account is opened by these presentation tests. */
@RunWith(AndroidJUnit4::class)
class LiveBotPanelTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private var starts = 0
    private var connections = 0
    private fun render(state: ConversationState) {
        ui.setContent {
            BotOsTheme(ThemeMode.LIGHT, true) {
                Box(Modifier.fillMaxSize()) {
                    LiveBotPanel(state, "", {}, {}, { starts++ }, {}, {}, { connections++ }, {}, {}, {})
                }
            }
        }
    }
    @Test fun signedOutConversationHasNoSendFieldAndOffersAccountConnection() {
        render(ConversationState("fixture_bot"))
        ui.onNodeWithTag("live-input").assertDoesNotExist()
        ui.onNodeWithTag("live-start").assertDoesNotExist()
        ui.onNodeWithTag("live-connect-account").performClick()
        ui.runOnIdle { assertEquals(1, connections); assertEquals(0, starts) }
    }
    @Test fun enteringConversationDoesNotStartBotAndPendingStatusIsVisible() {
        val key = ChatKey("fixture", "100")
        val timeline = MessageTimeline(key, listOf(BotMessage(1, key, 1, listOf(Block.Paragraph("text", "رسالة اختبار")), true, DeliveryState.PENDING)))
        render(ConversationState("fixture_bot", ConversationStatus.READY, timeline))
        ui.onNodeWithTag("delivery-1").assertIsDisplayed()
        ui.onNodeWithTag("live-send").assertIsNotEnabled()
        ui.runOnIdle { assertEquals(0, starts) }
        ui.onNodeWithTag("live-start").performClick()
        ui.runOnIdle { assertEquals(1, starts) }
    }
    @Test fun attachmentMenuOffersOnlyChosenKindsAndDoesNotSendOnOpening() {
        val key = ChatKey("fixture", "100")
        var chosen: AttachmentKind? = null
        ui.setContent {
            BotOsTheme(ThemeMode.LIGHT, true) {
                LiveBotPanel(ConversationState("fixture_bot", ConversationStatus.READY, MessageTimeline(key)),
                    "", {}, {}, {}, {}, {}, {}, {}, {}, {},
                    attachmentEnabled = true, onAttach = { chosen = it })
            }
        }
        ui.onNodeWithTag("outgoing-add").performClick()
        ui.onNodeWithTag("outgoing-option-photo").assertIsDisplayed()
        ui.onNodeWithTag("outgoing-option-video").assertIsDisplayed()
        ui.onNodeWithTag("outgoing-option-audio").assertIsDisplayed()
        ui.onNodeWithTag("outgoing-option-document").performClick()
        ui.runOnIdle { assertEquals(AttachmentKind.DOCUMENT, chosen) }
        ui.onNodeWithTag("outgoing-preview").assertDoesNotExist()
    }
    @Test fun messageButtonsRenderBelowCompactBubbleAndMetadataStaysInsideBubble() {
        val key = ChatKey("fixture", "100")
        val message = BotMessage(7, key, 3, listOf(
            Block.Paragraph("text", "/start"), Block.Buttons("actions", listOf(
                BotButton("inline/0/0", "فتح", ActionPayload.Callback("AQID")),
                BotButton("inline/0/1", "إعدادات", ActionPayload.Callback("BAUG")),
            ))), outgoing = true, delivery = DeliveryState.SENT, date = 1_789_000_000)
        render(ConversationState("fixture_bot", ConversationStatus.READY, MessageTimeline(key, listOf(message))))
        val bubble = ui.onNodeWithTag("message-bubble-7").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val buttons = ui.onNodeWithTag("message-buttons-7-actions").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val root = ui.onRoot().fetchSemanticsNode().boundsInRoot
        ui.onNodeWithTag("message-meta-7").assertIsDisplayed()
        ui.runOnIdle {
            assertTrue("Buttons must be outside and below the message bubble", buttons.top >= bubble.bottom - 1f)
            assertTrue("Outgoing bubble must not consume the full chat width", bubble.width < root.width * .9f)
        }
    }
    @Test fun workspaceUsesDropdownSwitcherInsteadOfPermanentBotTabs() {
        val bots = listOf(SavedBot("one", "first_bot", "الأول"), SavedBot("two", "second_bot", "الثاني"))
        var selected by mutableStateOf("one")
        val key = ChatKey("fixture", "100")
        ui.setContent {
            BotOsTheme(ThemeMode.LIGHT, true) {
                WorkspaceScreen(snapshot = StoreSnapshot(Workspace(bots = bots)), selectedId = selected,
                    timeline = MessageTimeline(key), draft = "", busy = false, onSelect = { selected = it },
                    onAdd = {}, onEdit = {}, onDelete = {}, onMove = { _, _ -> }, onOpenTelegram = {},
                    onDraft = {}, onSend = {}, onAction = {},
                    liveContent = { Box(Modifier.fillMaxSize().testTag("fixture-live-content")) })
            }
        }
        ui.onNodeWithTag("compact-bot-header").assertIsDisplayed()
        ui.onNodeWithTag("bot-switcher").assertIsDisplayed()
        ui.onNodeWithTag("bot-tab-one").assertDoesNotExist()
        ui.onNodeWithTag("bot-switcher").performClick()
        ui.onNodeWithTag("bot-switch-item-two").assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals("two", selected) }
        ui.onNodeWithTag("compact-bot-header").assertIsDisplayed()
        ui.onNodeWithText("@second_bot").assertIsDisplayed()
    }
}
