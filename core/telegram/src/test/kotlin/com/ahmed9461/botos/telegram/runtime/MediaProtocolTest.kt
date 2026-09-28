package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class MediaProtocolTest {
    private val chat = ChatKey("fixture", "100")
    private fun f(id: Int) = TdJson.command("file") { put("id", id); put("size", 512) }
    private fun caption() = TdJson.command("formattedText") { put("text", "وصف عربي") }
    @Test fun supportedOrdinaryMediaUseTheSameRichMediaPresentation() {
        val types = listOf(
            Triple("messageVideo", "video", "video"), Triple("messageAnimation", "animation", "animation"),
            Triple("messageAudio", "audio", "audio"), Triple("messageVoiceNote", "voice_note", "voice"),
            Triple("messageDocument", "document", "document"), Triple("messageVideoNote", "video_note", "video"),
        )
        types.forEach { (type, field, file) ->
            val raw = TdJson.command(type) {
                put("caption", caption()); put(field, TdJson.command(field) { put(file, f(11)); put("duration", 3) })
            }
            val result = StandardMediaAdapter.blocks(raw)!!.single() as Block.Media
            assertEquals(11, result.info.fileId)
            assertEquals("وصف عربي", result.info.caption.plainText())
        }
    }
    @Test fun photoChoosesBoundedDisplayResolutionWithoutInventedUrl() {
        val raw = TdJson.command("messagePhoto") { put("photo", TdJson.command("photo") {
            put("sizes", buildJsonArray { for (width in listOf(90, 800, 4000)) add(TdJson.command("photoSize") {
                put("width", width); put("height", width); put("photo", f(width))
            }) })
        }) }
        val info = (StandardMediaAdapter.blocks(raw)!!.single() as Block.Media).info
        assertEquals(800, info.fileId)
        assertNull(info.url)
    }
    @Test fun stickerFormatsStayDistinctForTheirDecoders() {
        val pairs = listOf("stickerFormatWebp" to "image/webp", "stickerFormatTgs" to "application/x-tgsticker", "stickerFormatWebm" to "video/webm")
        pairs.forEach { (type, mime) ->
            val raw = TdJson.command("messageSticker") { put("sticker", TdJson.command("sticker") {
                put("sticker", f(6)); put("format", TdJson.command(type)); put("emoji", "🙂")
            }) }
            val info = (StandardMediaAdapter.blocks(raw)!!.single() as Block.Media).info
            assertEquals(mime, info.mimeType); assertEquals(6, info.fileId)
        }
    }
    @Test fun protectedMediaIsNotExposedAndSpoilerMediaRequiresExpansion() {
        val raw = TdJson.command("messageVideo") {
            put("video", TdJson.command("video") { put("video", f(4)) }); put("has_spoiler", true)
        }
        assertTrue(StandardMediaAdapter.blocks(raw)!!.single() is Block.Details)
        assertTrue(StandardMediaAdapter.blocks(JsonObject(raw + ("is_secret" to JsonPrimitive(true))))!!.single() is Block.Unsupported)
        val selfDestruct = wireMessage().toMutableMap().apply {
            put("content", raw); put("self_destruct_type", TdJson.command("messageSelfDestructTypeImmediately"))
        }
        assertTrue(BotMessageAdapter.message(JsonObject(selfDestruct), chat, 1)!!.blocks.single() is Block.Unsupported)
    }
    @Test fun inputMediaUsesCurrentNestedTdlibSchema() {
        val expected = mapOf(AttachmentKind.PHOTO to "photo", AttachmentKind.VIDEO to "video",
            AttachmentKind.AUDIO to "audio", AttachmentKind.VOICE to "voice_note", AttachmentKind.DOCUMENT to "document")
        expected.forEach { (kind, field) ->
            val content = OutgoingMedia.content(PreparedAttachment("/private/staged/file", kind, 128, 100, 100, 2), "وصف")
            val inner = content.obj(field)!!
            assertEquals("inputFileLocal", inner.obj(field)!!.type())
            assertEquals("وصف", content.obj("caption")!!.string("text"))
        }
    }
    @Test fun invalidAttachmentSizeAndCaptionFailBeforeRpc() {
        for (size in listOf(0L, -1L, OutgoingMedia.MAX_BYTES + 1)) {
            try { OutgoingMedia.content(PreparedAttachment("/private/file", AttachmentKind.VIDEO, size), ""); fail("bad size") }
            catch (_: IllegalArgumentException) { }
        }
        try { OutgoingMedia.content(PreparedAttachment("/private/file", AttachmentKind.VIDEO, 1), "x".repeat(1025)); fail("caption") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun incompleteLocalFileNeverExposesAReadablePath() {
        val raw = TdJson.command("file") { put("id", 1); put("size", 100); put("local", TdJson.command("localFile") {
            put("path", "/private/partial"); put("downloaded_size", 99); put("is_downloading_completed", false)
        }) }
        assertNull(fileState(raw).path)
        assertEquals(TransferStage.DOWNLOADING, fileState(raw).stage)
    }
    @Test fun adapterNoLongerFallsBackForOrdinaryDocument() {
        val raw = wireMessage().toMutableMap()
        raw["content"] = TdJson.command("messageDocument") { put("document", TdJson.command("document") { put("document", f(4)) }) }
        assertTrue(BotMessageAdapter.message(JsonObject(raw), chat, 1)!!.blocks.single() is Block.Media)
    }
}
