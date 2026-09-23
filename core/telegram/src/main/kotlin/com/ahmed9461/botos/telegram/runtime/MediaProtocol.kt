package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.*
import kotlinx.serialization.json.*

/** Schema adapters; no Android decoders or network access in this layer. */
internal object StandardMediaAdapter {
    fun blocks(content: JsonObject): List<Block>? {
        if (content.flag("is_secret")) return listOf(Block.Unsupported("protected-media"))
        val info = when (content.type()) {
            "messagePhoto" -> photo(content.obj("photo"))
            "messageVideo" -> file(content.obj("video"), "video", MediaKind.VIDEO)
            "messageAnimation" -> file(content.obj("animation"), "animation", MediaKind.ANIMATION)
            "messageAudio" -> file(content.obj("audio"), "audio", MediaKind.AUDIO)
            "messageVoiceNote" -> file(content.obj("voice_note"), "voice", MediaKind.VOICE_NOTE)
            "messageVideoNote" -> file(content.obj("video_note"), "video", MediaKind.VIDEO)
            "messageDocument" -> file(content.obj("document"), "document", MediaKind.DOCUMENT)
            "messageSticker" -> sticker(content.obj("sticker"))
            else -> return null
        } ?: return listOf(Block.Unsupported("invalid-media"))
        val caption = content.obj("caption")?.string("text").orEmpty().take(ContentLimits.MAX_TEXT)
        val result = listOf(Block.Media("media", info.copy(caption = StyledText.plain(caption))))
        return if (content.flag("has_spoiler")) listOf(Block.Details("media-spoiler", "•••", result)) else result
    }
    private fun photo(photo: JsonObject?): MediaInfo? {
        val sizes = photo?.array("sizes")?.take(20)?.mapNotNull { it as? JsonObject }.orEmpty()
        val selected = sizes.filter { (it.number("width") ?: 0) <= 1280 }
            .maxByOrNull { (it.number("width") ?: 0) * (it.number("height") ?: 0) }
            ?: sizes.minByOrNull { (it.number("width") ?: Long.MAX_VALUE) }
        return file(selected, "photo", MediaKind.PHOTO)?.copy(mimeType = "image/jpeg")
    }
    private fun file(media: JsonObject?, field: String, kind: MediaKind): MediaInfo? {
        val file = media?.obj(field) ?: return null
        val id = file.number("id")?.takeIf { it in 1..Int.MAX_VALUE }?.toInt() ?: return null
        val title = listOf(media.string("title"), media.string("performer"))
            .filter(String::isNotBlank).joinToString(" — ").ifBlank { media.string("file_name") }
        return MediaInfo(kind, title = title.take(512), fileId = id,
            fileName = media.string("file_name").take(512), mimeType = media.string("mime_type").take(128),
            size = file.number("size"), durationSeconds = media.number("duration")?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt(),
            width = media.number("width")?.coerceIn(0, 65536)?.toInt(), height = media.number("height")?.coerceIn(0, 65536)?.toInt())
    }
    private fun sticker(sticker: JsonObject?): MediaInfo? {
        val format = sticker?.obj("format")?.type() ?: return null
        val mime = when (format) {
            "stickerFormatWebp" -> "image/webp"
            "stickerFormatTgs" -> "application/x-tgsticker"
            "stickerFormatWebm" -> "video/webm"
            else -> return null
        }
        val kind = if (format == "stickerFormatWebp") MediaKind.PHOTO else MediaKind.ANIMATION
        return file(sticker, "sticker", kind)?.copy(title = sticker.string("emoji").take(32), mimeType = mime)
    }
}

enum class AttachmentKind { PHOTO, VIDEO, AUDIO, VOICE, DOCUMENT }
/** Created from a validated app-private staged file. Never persist this object in logs. */
class PreparedAttachment(val path: String, val kind: AttachmentKind, val bytes: Long,
    val width: Int = 0, val height: Int = 0, val duration: Int = 0, val fileName: String = "") {
    override fun toString() = "PreparedAttachment(kind=$kind)"
}
internal object OutgoingMedia {
    const val MAX_BYTES = 50L * 1024 * 1024
    fun content(file: PreparedAttachment, caption: String): JsonObject {
        require(file.path.startsWith('/') && file.path.none(Char::isISOControl) && file.path.length <= 4096)
        require(file.bytes in 1..MAX_BYTES && file.width in 0..65536 && file.height in 0..65536 && file.duration >= 0)
        require(caption.length <= 1024)
        val input = TdJson.command("inputFileLocal") { put("path", file.path) }
        val text = TdJson.command("formattedText") { put("text", caption); put("entities", JsonArray(emptyList())) }
        val (messageType, field, payload) = when (file.kind) {
            AttachmentKind.PHOTO -> Triple("inputMessagePhoto", "photo", TdJson.command("inputPhoto") {
                put("photo", input); put("width", file.width); put("height", file.height)
                put("added_sticker_file_ids", JsonArray(emptyList()))
            })
            AttachmentKind.VIDEO -> Triple("inputMessageVideo", "video", TdJson.command("inputVideo") {
                put("video", input); put("width", file.width); put("height", file.height); put("duration", file.duration)
                put("supports_streaming", true); put("added_sticker_file_ids", JsonArray(emptyList()))
            })
            AttachmentKind.AUDIO -> Triple("inputMessageAudio", "audio", TdJson.command("inputAudio") {
                put("audio", input); put("duration", file.duration); put("title", file.fileName.take(256)); put("performer", "")
            })
            AttachmentKind.VOICE -> Triple("inputMessageVoiceNote", "voice_note", TdJson.command("inputVoiceNote") {
                put("voice_note", input); put("duration", file.duration); put("waveform", "")
            })
            AttachmentKind.DOCUMENT -> Triple("inputMessageDocument", "document", TdJson.command("inputDocument") {
                put("document", input); put("disable_content_type_detection", true)
            })
        }
        return TdJson.command(messageType) { put(field, payload); put("caption", text) }
    }
}

/** Attachment references come from the bounded presentation, never user-entered paths. */
fun mediaFileIds(blocks: List<Block>): Set<Int> = buildSet {
    var remaining = ContentLimits.MAX_BLOCKS
    fun walk(items: List<Block>, depth: Int) {
        if (depth >= ContentLimits.MAX_DEPTH) return
        for (item in items) {
            if (remaining-- <= 0) return
            when (item) {
                is Block.Media -> item.info.fileId?.let { if (it > 0) add(it) }
                is Block.RichDetails -> walk(item.children, depth + 1)
                is Block.Details -> walk(item.children, depth + 1)
                is Block.RichQuote -> walk(item.children, depth + 1)
                is Block.Gallery -> walk(item.children, depth + 1)
                is Block.RichList -> item.items.forEach { walk(it.blocks, depth + 1) }
                else -> Unit
            }
        }
    }
    walk(blocks, 0)
}
