package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PendingRepliesTest {
    private var now = 1_000L
    private val key = ChatKey("fixture", "100")
    private fun pending(id: Long = 1, text: String = "مرحبا", topic: Int = 0, keep: Boolean = false) = TdJson.command("updatePendingMessage") {
        put("chat_id", 100); put("forum_topic_id", topic); put("draft_id", id); put("can_stop", true); put("keep_on_stop", keep)
        put("content", TdJson.command("messageText") { put("text", TdJson.command("formattedText") { put("text", text) }) })
    }
    @Test fun fragmentsReplaceRatherThanAccumulateAndNeverBecomeHistory() {
        val r = PendingReplies(key, 100, 3_000) { now }
        repeat(100) { r.update(pending(text = "جزء $it")) }
        assertEquals("جزء 99", (r.value!!.content.blocks.single() as Block.Paragraph).text)
        assertEquals(Long.MIN_VALUE, r.value!!.content.id)
        assertEquals(0L, r.value!!.content.date)
        assertTrue(r.value!!.content.inlineActions.isEmpty())
    }
    @Test fun deadlineUsesProtocolPeriodAndRefreshesOnANewFragment() {
        val r = PendingReplies(key, 100, 3_000) { now }
        r.update(pending()); now += 2_999; assertFalse(r.expire())
        r.update(pending(text = "تحديث")); now += 2_999; assertFalse(r.expire())
        now++; assertTrue(r.expire()); assertNull(r.value)
    }
    @Test fun incomingFinalClearsPendingButOutgoingDoesNot() {
        val r = PendingReplies(key, 100, 3_000) { now }
        r.update(pending()); r.incoming(wireMessage(outgoing = true)); assertNotNull(r.value)
        r.incoming(wireMessage()); assertNull(r.value)
    }
    @Test fun topicAndChatIsolationAndWrongStopAreEnforced() {
        val r = PendingReplies(key, 100, 3_000) { now }
        r.update(pending()); r.update(pending(id = 2, topic = 2)); assertEquals(1L, r.value!!.draftId)
        r.incoming(wireMessage(chatId = 200)); r.stop(7); assertNotNull(r.value)
        r.update(TdJson.command("updateStopMessageDraft") { put("chat_id", 200); put("draft_id", 1) })
        assertNotNull(r.value)
        r.stop(1); assertNull(r.value)
    }
    @Test fun keepOnStopDisablesStopAndStillExpires() {
        val r = PendingReplies(key, 100, 3_000) { now }
        r.update(pending(keep = true)); r.stop(1)
        assertTrue(r.value!!.stopped); assertFalse(r.value!!.canStop)
        now += 3_000; assertTrue(r.expire())
    }
    @Test fun missingPeriodDoesNotGuessAnUnlimitedLifetime() {
        val r = PendingReplies(key, 100, 0) { now }
        r.update(pending()); assertNull(r.value)
    }
    @Test fun newDraftReplacesOldAndRichButtonsAreDisabledUntilFinal() {
        val r = PendingReplies(key, 100, 3_000) { now }
        r.update(pending()); r.update(pending(2)); assertEquals(2L, r.value!!.draftId)
        val rich = TdJson.command("updatePendingMessage") {
            put("chat_id", 100); put("draft_id", 3); put("content", TdJson.command("messageRichMessage") {
                put("message", TdJson.command("richMessage") { put("is_full", true); put("blocks", buildJsonArray {
                    add(TdJson.command("pageBlockParagraph") { put("text", TdJson.command("richTextBold") {
                        put("text", TdJson.command("richTextPlain") { put("text", "غني") })
                    }) })
                    add(TdJson.command("pageBlockButtonRow") { put("buttons", buildJsonArray {
                        add(TdJson.command("inlineButton") {
                            put("text", TdJson.command("richTextPlain") { put("text", "قسم" ) })
                            put("type", TdJson.command("inlineKeyboardButtonTypeCallback") { put("data", "AQID") })
                        })
                    }) })
                }) })
            })
        }
        r.update(rich)
        assertTrue(RichMark.BOLD in (r.value!!.content.blocks.first() as Block.Paragraph).content.spans.single().marks)
        val action = (r.value!!.content.blocks.last() as Block.Buttons).buttons.single()
        assertFalse(action.enabled); assertEquals(ActionPayload.Unsupported, action.payload)
        assertTrue(r.value!!.content.inlineActions.isEmpty())
    }

    @Test fun textAndRichStreamFragmentsReplaceInPlaceAndOldStopCannotEraseNewDraft() {
        val r = PendingReplies(key, 100, 3_000) { now }
        r.update(pending(id = 10, text = "الجزء الأول"))
        val textRevision = r.value!!.content.revision
        fun rich(fragment: String) = TdJson.command("updatePendingMessage") {
            put("chat_id", 100); put("draft_id", 11); put("can_stop", true)
            put("content", TdJson.command("messageRichMessage") {
                put("message", TdJson.command("richMessage") {
                    put("is_full", true)
                    put("blocks", buildJsonArray { add(TdJson.command("pageBlockParagraph") {
                        put("text", TdJson.command("richTextPlain") { put("text", fragment) })
                    }) })
                })
            })
        }
        r.update(rich("قديم")); r.update(rich("جديد"))
        val current = r.value!!
        assertEquals(11L, current.draftId)
        assertTrue(current.content.revision > textRevision)
        assertEquals("جديد", (current.content.blocks.single() as Block.Paragraph).text)
        assertTrue(current.content.inlineActions.isEmpty())
        r.update(TdJson.command("updateStopMessageDraft") { put("chat_id", 100); put("draft_id", 10) })
        assertEquals(11L, r.value!!.draftId)
        r.incoming(wireMessage(chatId = 200)); assertNotNull(r.value)
        r.incoming(wireMessage(chatId = 100)); assertNull(r.value)
    }
}
