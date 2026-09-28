package com.ahmed9461.botos.model

import org.junit.Assert.*
import org.junit.Test

class MessageMediaTest {
    private val chat = ChatKey("42:1", "100:1")
    private val media = Block.Media("photo", MediaInfo(MediaKind.PHOTO, fileId = 7))
    @Test fun referenceBindsMessageRevisionAndNestedBlock() {
        val message = BotMessage(5, chat, 4, listOf(Block.RichDetails("details", StyledText.plain("صورة"), listOf(media))))
        val index = messageMediaIndex(MessageTimeline(chat, listOf(message)))
        assertEquals(7, index[MediaReference(chat, 5, 4, "photo")]?.fileId)
        assertNull(index[MediaReference(chat, 5, 3, "photo")])
    }
    @Test fun foreignAccountAndPendingContentNeverEnterCatalog() {
        val other = ChatKey("43:1", "100:1")
        val messages = listOf(BotMessage(1, other, 1, listOf(media)), BotMessage(Long.MIN_VALUE, chat, 1, listOf(media)))
        assertTrue(messageMediaIndex(MessageTimeline(chat, messages)).isEmpty())
    }
    @Test fun duplicateBlockIdentitiesAreRejectedRatherThanChoosingWrongAttachment() {
        val message = BotMessage(5, chat, 1, listOf(media, media.copy(info = MediaInfo(MediaKind.PHOTO, fileId = 8))))
        assertTrue(messageMediaIndex(MessageTimeline(chat, listOf(message))).isEmpty())
    }
    @Test fun removingMessageRemovesItsFileAuthority() {
        val timeline = MessageTimeline(chat, listOf(BotMessage(5, chat, 1, listOf(media))))
        assertEquals(1, messageMediaIndex(timeline).size)
        assertTrue(messageMediaIndex(timeline.delete(5)).isEmpty())
    }
    @Test fun depthAndTotalVisitBudgetBoundCatalogWork() {
        var nested: Block = media
        repeat(100) { nested = Block.Gallery("gallery-$it", listOf(nested)) }
        assertTrue(messageMediaIndex(MessageTimeline(chat, listOf(BotMessage(5, chat, 1, listOf(nested))))).isEmpty())
        val many = List(3000) { media.copy(id = "m-$it") }
        assertTrue(messageMediaIndex(MessageTimeline(chat, listOf(BotMessage(5, chat, 1, many)))).size <= 2048)
    }
}
