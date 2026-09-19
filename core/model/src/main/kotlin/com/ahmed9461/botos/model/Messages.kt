package com.ahmed9461.botos.model

/** Protocol-independent presentation model, not an invented Telegram wire schema. */
data class ChatKey(val account: String, val chat: String)

sealed interface ActionPayload {
    data class Callback(val opaqueData: String) : ActionPayload
    data class SendText(val text: String) : ActionPayload
    data class OpenUrl(val url: String) : ActionPayload
    data object Unsupported : ActionPayload
}

enum class ButtonVisualStyle { DEFAULT, PRIMARY, DANGER, SUCCESS, LINK }
data class BotButton(
    val id: String,
    val label: String,
    val payload: ActionPayload,
    val enabled: Boolean = true,
    val style: ButtonVisualStyle = ButtonVisualStyle.DEFAULT,
)

enum class RichMark {
    BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, SPOILER, SUBSCRIPT, SUPERSCRIPT, MARKED, CODE, MATH,
}
data class StyledSpan(
    val text: String,
    val marks: Set<RichMark> = emptySet(),
    val url: String? = null,
)
data class StyledText(val spans: List<StyledSpan> = emptyList()) {
    fun plainText(): String = spans.joinToString(separator = "") { it.text }
    fun isBlank(): Boolean = spans.all { it.text.isBlank() }
    companion object {
        val EMPTY = StyledText()
        fun plain(text: String) = if (text.isEmpty()) EMPTY else StyledText(listOf(StyledSpan(text)))
    }
}

data class RichListItem(
    val label: String = "",
    val blocks: List<Block>,
    val checked: Boolean? = null,
)
data class RichTableCell(
    val content: StyledText,
    val isHeader: Boolean = false,
    val colspan: Int = 1,
    val rowspan: Int = 1,
)
enum class MediaKind { PHOTO, VIDEO, ANIMATION, AUDIO, VOICE_NOTE, DOCUMENT, MAP, EMBEDDED, CHAT_LINK }
data class MediaInfo(
    val kind: MediaKind,
    val title: String = "",
    val caption: StyledText = StyledText.EMPTY,
    val fileId: Int? = null,
    val fileName: String = "",
    val mimeType: String = "",
    val size: Long? = null,
    val durationSeconds: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val url: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

sealed interface Block {
    val id: String

    data class Heading(
        override val id: String,
        val content: StyledText,
        val level: Int = 1,
    ) : Block {
        constructor(id: String, text: String) : this(id, StyledText.plain(text))
        val text: String get() = content.plainText()
    }

    data class Paragraph(
        override val id: String,
        val content: StyledText,
    ) : Block {
        constructor(id: String, text: String) : this(id, StyledText.plain(text))
        val text: String get() = content.plainText()
    }

    data class Quote(
        override val id: String,
        val content: StyledText,
        val credit: StyledText? = null,
        val expandable: Boolean = false,
    ) : Block {
        constructor(id: String, text: String) : this(id, StyledText.plain(text))
        val text: String get() = content.plainText()
    }

    data class Code(
        override val id: String,
        val content: StyledText,
        val language: String = "",
    ) : Block {
        constructor(id: String, text: String) : this(id, StyledText.plain(text))
        val text: String get() = content.plainText()
    }

    data class Bullets(override val id: String, val items: List<String>) : Block
    data class RichList(override val id: String, val items: List<RichListItem>) : Block
    data class Table(override val id: String, val headers: List<String>, val rows: List<List<String>>) : Block
    data class RichTable(
        override val id: String,
        val caption: StyledText = StyledText.EMPTY,
        val rows: List<List<RichTableCell>>,
        val compact: Boolean = false,
    ) : Block
    data class Details(override val id: String, val title: String, val children: List<Block>) : Block
    data class RichDetails(override val id: String, val header: StyledText, val children: List<Block>, val initiallyOpen: Boolean = false) : Block
    data class Buttons(override val id: String, val buttons: List<BotButton>) : Block
    data class Divider(override val id: String) : Block
    data class Math(override val id: String, val expression: String) : Block
    data class Media(override val id: String, val info: MediaInfo) : Block
    data class Gallery(
        override val id: String,
        val children: List<Block>,
        val caption: StyledText = StyledText.EMPTY,
        val slideshow: Boolean = false,
    ) : Block
    data class Unsupported(override val id: String) : Block
}

enum class DeliveryState { NONE, PENDING, SENT, FAILED }
data class BotMessage(
    val id: Long,
    val chat: ChatKey,
    val revision: Long,
    val blocks: List<Block>,
    val outgoing: Boolean = false,
    val delivery: DeliveryState = DeliveryState.NONE,
    val date: Long = 0L,
    val forceRtl: Boolean? = null,
)
data class ActionTicket(val chat: ChatKey, val messageId: Long, val revision: Long, val buttonId: String)

object ContentLimits {
    const val MAX_MESSAGES = 200
    const val MAX_BLOCKS = 500
    const val MAX_DEPTH = 16
    const val MAX_TEXT = 32_768
    const val MAX_COLUMNS = 20
    const val MAX_ROWS = 100
    const val MAX_MEDIA = 50
}

/** Immutable per-chat state. Edits replace in-place; delayed updates cannot roll back revisions. */
data class MessageTimeline(val chat: ChatKey, val messages: List<BotMessage> = emptyList()) {
    fun upsert(message: BotMessage): MessageTimeline {
        if (message.chat != chat) return this
        val index = messages.indexOfFirst { it.id == message.id }
        if (index >= 0 && messages[index].revision >= message.revision) return this
        val bounded = message.copy(blocks = ContentSafety.bound(message.blocks))
        val updated = messages.toMutableList()
        if (index < 0) updated.add(bounded) else updated[index] = bounded
        return copy(messages = updated.takeLast(ContentLimits.MAX_MESSAGES))
    }

    fun delete(messageId: Long) = copy(messages = messages.filterNot { it.id == messageId })

    fun resolve(ticket: ActionTicket): BotButton? {
        if (ticket.chat != chat) return null
        val message = messages.singleOrNull { it.id == ticket.messageId && it.revision == ticket.revision } ?: return null
        val buttons = mutableListOf<BotButton>()
        var remaining = ContentLimits.MAX_BLOCKS
        fun walk(blocks: List<Block>, depth: Int) {
            if (depth >= ContentLimits.MAX_DEPTH) return
            blocks.take(ContentLimits.MAX_BLOCKS).forEach {
                if (remaining-- <= 0) return
                when (it) {
                    is Block.Buttons -> buttons.addAll(it.buttons.take(8))
                    is Block.Details -> walk(it.children, depth + 1)
                    is Block.RichDetails -> walk(it.children, depth + 1)
                    is Block.RichList -> it.items.forEach { item -> walk(item.blocks, depth + 1) }
                    is Block.Gallery -> walk(it.children, depth + 1)
                    else -> Unit
                }
            }
        }
        walk(message.blocks, 0)
        return buttons.singleOrNull { it.id == ticket.buttonId && it.enabled && it.payload != ActionPayload.Unsupported }
    }
}

/** One total budget per message, not an exponentially growing budget per nested branch. */
object ContentSafety {
    fun bound(blocks: List<Block>): List<Block> {
        var remaining = ContentLimits.MAX_BLOCKS
        var mediaRemaining = ContentLimits.MAX_MEDIA

        fun text(value: StyledText, max: Int): StyledText {
            var left = max
            if (left <= 0) return StyledText.EMPTY
            val spans = buildList {
                for (span in value.spans) {
                    if (left <= 0) break
                    val part = span.text.take(left)
                    if (part.isNotEmpty()) add(span.copy(text = part, url = span.url?.take(4096)))
                    left -= part.length
                }
            }
            return StyledText(spans)
        }

        fun visit(items: List<Block>, depth: Int): List<Block> {
            if (depth >= ContentLimits.MAX_DEPTH) return listOf(Block.Unsupported("depth-limit"))
            val result = mutableListOf<Block>()
            for (item in items) {
                if (remaining-- <= 0) {
                    result.add(Block.Unsupported("content-limit"))
                    break
                }
                result.add(
                    when (item) {
                        is Block.Paragraph -> item.copy(content = text(item.content, ContentLimits.MAX_TEXT))
                        is Block.Heading -> item.copy(content = text(item.content, 512), level = item.level.coerceIn(1, 6))
                        is Block.Quote -> item.copy(content = text(item.content, ContentLimits.MAX_TEXT), credit = item.credit?.let { text(it, 512) })
                        is Block.Code -> item.copy(content = text(item.content, ContentLimits.MAX_TEXT), language = item.language.take(64))
                        is Block.Bullets -> item.copy(items = item.items.take(100).map { it.take(512) })
                        is Block.RichList -> item.copy(items = item.items.take(100).map { entry ->
                            entry.copy(label = entry.label.take(64), blocks = visit(entry.blocks, depth + 1))
                        })
                        is Block.Table -> item.copy(
                            headers = item.headers.take(ContentLimits.MAX_COLUMNS).map { it.take(256) },
                            rows = item.rows.take(ContentLimits.MAX_ROWS).map { row ->
                                row.take(ContentLimits.MAX_COLUMNS).map { it.take(512) }
                            },
                        )
                        is Block.RichTable -> item.copy(
                            caption = text(item.caption, 512),
                            rows = item.rows.take(ContentLimits.MAX_ROWS).map { row ->
                                row.take(ContentLimits.MAX_COLUMNS).map { cell ->
                                    cell.copy(
                                        content = text(cell.content, 512),
                                        colspan = cell.colspan.coerceIn(1, ContentLimits.MAX_COLUMNS),
                                        rowspan = cell.rowspan.coerceIn(1, ContentLimits.MAX_ROWS),
                                    )
                                }
                            },
                        )
                        is Block.Details -> item.copy(title = item.title.take(256), children = visit(item.children, depth + 1))
                        is Block.RichDetails -> item.copy(header = text(item.header, 256), children = visit(item.children, depth + 1))
                        is Block.Buttons -> item.copy(buttons = item.buttons.take(8).map {
                            it.copy(label = it.label.take(256))
                        })
                        is Block.Divider -> item
                        is Block.Math -> item.copy(expression = item.expression.take(ContentLimits.MAX_TEXT))
                        is Block.Media -> {
                            if (mediaRemaining-- <= 0) Block.Unsupported("media-limit") else item.copy(info = item.info.copy(
                                title = item.info.title.take(512),
                                caption = text(item.info.caption, 2048),
                                fileName = item.info.fileName.take(512),
                                mimeType = item.info.mimeType.take(256),
                                url = item.info.url?.take(4096),
                            ))
                        }
                        is Block.Gallery -> item.copy(caption = text(item.caption, 1024), children = visit(item.children, depth + 1))
                        is Block.Unsupported -> item
                    },
                )
            }
            return result
        }
        return visit(blocks, 0)
    }
}
