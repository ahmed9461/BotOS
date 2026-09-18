package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

internal fun wireMessage(id: Long = 10, chatId: Long = 100, text: String = "مرحبا 🌚", outgoing: Boolean = false,
    sending: String? = null, markup: JsonObject? = null): JsonObject = TdJson.command("message") {
    put("id", id); put("chat_id", chatId); put("is_outgoing", outgoing)
    put("content", TdJson.command("messageText") { put("text", TdJson.command("formattedText") { put("text", text) }) })
    sending?.let { put("sending_state", TdJson.command(it)) }
    markup?.let { put("reply_markup", it) }
}
internal fun wireKeyboard(inline: Boolean, type: String = if (inline) "inlineKeyboardButtonTypeCallback" else "keyboardButtonTypeText",
    data: String = "AP+AAQ==", oneTime: Boolean = false): JsonObject = TdJson.command(if (inline) "replyMarkupInlineKeyboard" else "replyMarkupShowKeyboard") {
    put("one_time", oneTime)
    put("rows", buildJsonArray { add(buildJsonArray { add(buildJsonObject {
        put("text", "قسم خاص"); put("type", TdJson.command(type) { put("data", data); put("url", data) })
    }) }) })
}

class BotMessagesTest {
    private val key = ChatKey("42:1", "100:2")
    @Test fun callbackBinaryDataAndRowsAreNotGuessedFromLabels() {
        val m = BotMessageAdapter.message(wireMessage(markup = wireKeyboard(true)), key, 1)!!
        val button = (m.blocks.last() as Block.Buttons).buttons.single()
        assertEquals(ActionPayload.Callback("AP+AAQ=="), button.payload)
        assertEquals("قسم خاص", button.label)
    }
    @Test fun specialButtonsAreExplicitlyDisabled() {
        val m = BotMessageAdapter.message(wireMessage(markup = wireKeyboard(true, "inlineKeyboardButtonTypeBuy")), key, 1)!!
        val b = (m.blocks.last() as Block.Buttons).buttons.single()
        assertFalse(b.enabled); assertEquals(ActionPayload.Unsupported, b.payload)
    }
    @Test fun updatesSupersedeLateHistoryAndInvalidateOldTickets() {
        val r = BotMessageReducer(key, 100)
        r.add(wireMessage(markup = wireKeyboard(true)))
        val m = r.timeline().messages.single()
        val t = ActionTicket(key, m.id, m.revision, "inline/0/0")
        assertNotNull(r.resolve(t))
        r.update(TdJson.command("updateMessageEdited") { put("chat_id", 100); put("message_id", 10); put("reply_markup", JsonNull) })
        r.history(listOf(wireMessage(markup = wireKeyboard(true))))
        assertNull(r.resolve(t)); assertFalse(r.timeline().messages.single().blocks.any { it is Block.Buttons })
    }
    @Test fun finalSendUpdateCannotBeRolledBackByLatePendingResponse() {
        val r = BotMessageReducer(key, 100)
        r.update(TdJson.command("updateMessageSendSucceeded") { put("old_message_id", -1); put("message", wireMessage(22, outgoing = true)) })
        r.add(wireMessage(-1, outgoing = true, sending = "messageSendingStatePending"))
        assertEquals(listOf(22L), r.timeline().messages.map { it.id })
        assertEquals(DeliveryState.SENT, r.timeline().messages.single().delivery)
    }
    @Test fun sendFailureMayKeepTheSameMessageId() {
        val r = BotMessageReducer(key, 100)
        r.add(wireMessage(-1, outgoing = true, sending = "messageSendingStatePending"))
        r.update(TdJson.command("updateMessageSendFailed") { put("old_message_id", -1); put("message", wireMessage(-1, outgoing = true, sending = "messageSendingStateFailed")) })
        assertEquals(DeliveryState.FAILED, r.timeline().messages.single().delivery)
    }
    @Test fun onlyCurrentReplyKeyboardCanExecuteAndNullUpdateHidesIt() {
        val r = BotMessageReducer(key, 100)
        r.setKeyboard(wireMessage(markup = wireKeyboard(false, oneTime = true)))
        val m = r.keyboard!!; val t = ActionTicket(key, m.id, m.revision, "reply/0/0")
        assertTrue(r.keyboardOneTime); assertEquals(ActionPayload.SendText("قسم خاص"), r.resolve(t)?.payload)
        r.update(TdJson.command("updateChatReplyMarkup") { put("chat_id", 100); put("reply_markup_message", JsonNull) })
        assertNull(r.keyboard); assertNull(r.resolve(t))
    }
    @Test fun foreignChatAndDeletedHistoryCannotAppearInCurrentTimeline() {
        val r = BotMessageReducer(key, 100)
        r.add(wireMessage(chatId = 200)); assertTrue(r.timeline().messages.isEmpty())
        r.add(wireMessage())
        r.update(TdJson.command("updateDeleteMessages") { put("chat_id", 100); put("is_permanent", true); put("message_ids", buildJsonArray { add(10) }) })
        r.history(listOf(wireMessage())); assertTrue(r.timeline().messages.isEmpty())
    }
    @Test fun linksRejectExecutableSchemesCredentialsAndControlCharacters() {
        assertEquals("https://example.org/path", safeBotUrl("https://example.org/path"))
        listOf("javascript:alert(1)", "file:///etc/passwd", "intent://open", "https://user:pass@example.org", "https://example.org/\n").forEach { assertNull(safeBotUrl(it)) }
    }
    @Test fun messageAndKeyboardBudgetsAreBounded() {
        val r = BotMessageReducer(key, 100)
        repeat(250) { r.add(wireMessage(it.toLong() + 1)) }
        assertEquals(ContentLimits.MAX_MESSAGES, r.timeline().messages.size)
        assertNull(r.resolve(ActionTicket(ChatKey("other", "100:2"), 250, 250, "inline/0/0")))
    }
}
