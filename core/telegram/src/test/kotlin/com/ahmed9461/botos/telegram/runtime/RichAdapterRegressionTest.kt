package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Schema-shaped fixtures only. Never uses a native session or a user's chat. */
class RichAdapterRegressionTest {
    private val key = ChatKey("fixture-account", "fixture-chat")
    private fun plain(text: String) = TdJson.command("richTextPlain") { put("text", text) }
    private fun paragraph(text: JsonObject) = TdJson.command("pageBlockParagraph") { put("text", text) }
    private fun rich(blocks: JsonArray, full: Boolean = true) = TdJson.command("richMessage") {
        put("is_full", full); put("is_rtl", true); put("blocks", blocks)
    }
    private fun message(content: JsonObject) = TdJson.command("message") {
        put("id", 50); put("chat_id", 100); put("date", 1_789_000_000)
        put("content", TdJson.command("messageRichMessage") { put("message", content) })
    }

    @Test fun normalKeyboardPreservesStyleAndBinaryCallbackData() {
        val markup = TdJson.command("replyMarkupInlineKeyboard") {
            put("rows", buildJsonArray { add(buildJsonArray {
                add(buildJsonObject {
                    put("text", "حذف"); put("style", TdJson.command("buttonStyleDanger"))
                    put("type", TdJson.command("inlineKeyboardButtonTypeCallback") { put("data", "AP+AAQ==") })
                })
            }) })
        }
        val model = BotMessageAdapter.message(wireMessage(markup = markup), key, 1)!!
        val button = (model.blocks.last() as Block.Buttons).buttons.single()
        assertEquals(ButtonVisualStyle.DANGER, button.style)
        assertEquals(ActionPayload.Callback("AP+AAQ=="), button.payload)
    }

    @Test fun nestedRichTextAndCoverStopAtTheActualParsingBudget() {
        var text = plain("لا يصل هذا العمق")
        repeat(100) { val inner = text; text = TdJson.command("richTextBold") { put("text", inner) } }
        var cover = paragraph(plain("داخل الغلاف"))
        repeat(100) { val inner = cover; cover = TdJson.command("pageBlockCover") { put("cover", inner) } }
        val model = BotMessageAdapter.message(message(rich(buildJsonArray { add(paragraph(text)); add(cover) })), key, 1)!!
        assertEquals("", (model.blocks.first() as Block.Paragraph).text)
        assertTrue(model.blocks.any { it is Block.Unsupported })
    }

    @Test fun linksAndInlineTextButtonsUseRevisionBoundActions() {
        val content = TdJson.command("richTexts") {
            put("texts", buildJsonArray {
                add(TdJson.command("richTextUrl") { put("text", plain("المصدر")); put("url", "https://example.org/help") })
                add(TdJson.command("richTextButton") { put("button", TdJson.command("inlineButton") {
                    put("text", plain("فتح")); put("type", TdJson.command("inlineKeyboardButtonTypeCallback") { put("data", "AQID") })
                }) })
            })
        }
        val model = BotMessageAdapter.message(message(rich(buildJsonArray { add(paragraph(content)) })), key, 7)!!
        assertEquals(2, model.inlineActions.size)
        val timeline = MessageTimeline(key, listOf(model))
        model.inlineActions.forEach { action ->
            assertEquals(action.payload, timeline.resolve(ActionTicket(key, 50, 7, action.id))?.payload)
            assertNull(timeline.resolve(ActionTicket(key, 50, 6, action.id)))
        }
        assertTrue((model.blocks.single() as Block.Paragraph).content.spans.all { it.actionId != null })
    }

    @Test fun fullRichResponseCannotOverwriteAnEditOrReintroduceADeletion() {
        val reducer = BotMessageReducer(key, 100)
        val partial = rich(buildJsonArray { add(paragraph(plain("جزئي"))) }, full = false)
        val full = rich(buildJsonArray { add(paragraph(plain("كامل"))) })
        reducer.add(message(partial))
        val old = reducer.timeline().messages.single()
        reducer.update(TdJson.command("updateMessageEdited") { put("chat_id", 100); put("message_id", 50); put("reply_markup", JsonNull) })
        val current = reducer.timeline().messages.single()
        assertFalse(reducer.completeRich(50, old.revision, full))
        assertTrue(reducer.completeRich(50, current.revision, full))
        assertTrue(reducer.timeline().messages.single().isFull)
        reducer.update(TdJson.command("updateDeleteMessages") {
            put("chat_id", 100); put("is_permanent", true); put("message_ids", buildJsonArray { add(50) })
        })
        assertFalse(reducer.completeRich(50, current.revision, full))
        assertTrue(reducer.timeline().messages.isEmpty())
    }

    @Test fun structuredQuoteKeepsCollapsibleChildrenAndCredit() {
        val quote = TdJson.command("pageBlockBlockQuote") {
            put("credit", plain("مصدر تجريبي"))
            put("blocks", buildJsonArray { add(TdJson.command("pageBlockDetails") {
                put("header", plain("التفاصيل")); put("is_open", false)
                put("blocks", buildJsonArray { add(paragraph(plain("نص"))) })
            }) })
        }
        val result = BotMessageAdapter.message(message(rich(buildJsonArray { add(quote) })), key, 1)!!
        val block = result.blocks.single() as Block.RichQuote
        assertTrue(block.children.single() is Block.RichDetails)
        assertEquals("مصدر تجريبي", block.credit.plainText())
    }
}
