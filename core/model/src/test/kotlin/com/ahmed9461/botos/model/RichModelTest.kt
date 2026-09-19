package com.ahmed9461.botos.model

import org.junit.Assert.*
import org.junit.Test

class RichModelTest {
    @Test fun timelineEditsPreserveTelegramTimestampAndInvalidatePreviousActions() {
        val chat = ChatKey("fixture-account", "fixture-chat")
        val button = BotButton("action", "قسم", ActionPayload.Callback("AQID"))
        val first = BotMessage(7, chat, 1, listOf(Block.Buttons("row", listOf(button))), date = 1_789_000_000)
        val oldTicket = ActionTicket(chat, 7, 1, "action")
        val timeline = MessageTimeline(chat).upsert(first)
        assertNotNull(timeline.resolve(oldTicket))
        val updated = timeline.upsert(first.copy(revision = 2, blocks = listOf(Block.Paragraph("text", "تعديل"))))
        assertEquals(first.date, updated.messages.single().date)
        assertNull(updated.resolve(oldTicket))
    }

    @Test fun oversizedFlatMessagesAreBoundedWithoutDroppingTheLimitSignal() {
        val input = List(ContentLimits.MAX_BLOCKS + 50) { Block.Paragraph("p-$it", "نص") }
        val bounded = ContentSafety.bound(input)
        assertEquals(ContentLimits.MAX_BLOCKS, bounded.count { it is Block.Paragraph })
        assertTrue(bounded.last() is Block.Unsupported)
    }
}
