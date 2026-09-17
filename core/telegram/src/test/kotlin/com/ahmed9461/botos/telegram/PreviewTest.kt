package com.ahmed9461.botos.telegram
import com.ahmed9461.botos.model.*
import org.junit.Test
import org.junit.Assert.*
class PreviewTest {
    @Test fun callbacksReplaceOneMessageAndInvalidateOldTickets() {
        val gateway = PreviewGateway()
        val chat = ChatKey("preview", "showcase")
        val original = gateway.initial(chat)
        val timeline = MessageTimeline(chat).upsert(original)
        val ticket = ActionTicket(chat, original.id, original.revision, "report")
        val changed = gateway.activate(original, requireNotNull(timeline.resolve(ticket)))!!
        assertEquals(original.id, changed.id)
        assertTrue(changed.blocks.any { it is Block.Table })
        assertNull(timeline.upsert(changed).resolve(ticket))
    }
    @Test fun previewNeverProcessesRealAccountContent() {
        val gateway = PreviewGateway()
        val real = BotMessage(1, ChatKey("real", "bot"), 1, emptyList())
        assertNull(gateway.activate(real, BotButton("id", "text", ActionPayload.Callback("preview:report"))))
    }
    @Test fun unknownActionIsNotFaked() {
        val gateway = PreviewGateway()
        val message = gateway.initial(ChatKey("preview", "showcase"))
        assertNull(gateway.activate(message, BotButton("id", "text", ActionPayload.Callback("unrecognized"))))
    }
}
