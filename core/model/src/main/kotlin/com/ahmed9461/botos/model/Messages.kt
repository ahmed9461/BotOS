package com.ahmed9461.botos.model

/** Protocol-independent presentation model, not an invented Telegram wire schema. */
data class ChatKey(val account: String, val chat: String)
sealed interface ActionPayload {
    data class Callback(val opaqueData: String) : ActionPayload
    data class SendText(val text: String) : ActionPayload
    data class OpenUrl(val url: String) : ActionPayload
    data object Unsupported : ActionPayload
}
data class BotButton(val id: String, val label: String, val payload: ActionPayload, val enabled: Boolean = true)
sealed interface Block {
    val id: String
    data class Heading(override val id: String, val text: String) : Block
    data class Paragraph(override val id: String, val text: String) : Block
    data class Quote(override val id: String, val text: String) : Block
    data class Code(override val id: String, val text: String) : Block
    data class Bullets(override val id: String, val items: List<String>) : Block
    data class Table(override val id: String, val headers: List<String>, val rows: List<List<String>>) : Block
    data class Details(override val id: String, val title: String, val children: List<Block>) : Block
    data class Buttons(override val id: String, val buttons: List<BotButton>) : Block
    data class Unsupported(override val id: String) : Block
}
data class BotMessage(
    val id: Long, val chat: ChatKey, val revision: Long,
    val blocks: List<Block>, val outgoing: Boolean = false,
)
data class ActionTicket(val chat: ChatKey, val messageId: Long, val revision: Long, val buttonId: String)

object ContentLimits {
    const val MAX_MESSAGES = 200
    const val MAX_BLOCKS = 64
    const val MAX_DEPTH = 8
    const val MAX_TEXT = 32_768
    const val MAX_COLUMNS = 20
    const val MAX_ROWS = 100
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
        fun visit(items: List<Block>, depth: Int): List<Block> {
            if (depth >= ContentLimits.MAX_DEPTH) return listOf(Block.Unsupported("depth-limit"))
            val result = mutableListOf<Block>()
            for (item in items) {
                if (remaining-- <= 0) { result.add(Block.Unsupported("content-limit")); break }
                result.add(when (item) {
                    is Block.Paragraph -> item.copy(text = item.text.take(ContentLimits.MAX_TEXT))
                    is Block.Heading -> item.copy(text = item.text.take(512))
                    is Block.Quote -> item.copy(text = item.text.take(ContentLimits.MAX_TEXT))
                    is Block.Code -> item.copy(text = item.text.take(ContentLimits.MAX_TEXT))
                    is Block.Bullets -> item.copy(items = item.items.take(100).map { it.take(512) })
                    is Block.Table -> item.copy(headers = item.headers.take(ContentLimits.MAX_COLUMNS).map { it.take(256) },
                        rows = item.rows.take(ContentLimits.MAX_ROWS).map { row -> row.take(ContentLimits.MAX_COLUMNS).map { it.take(512) } })
                    is Block.Details -> item.copy(title = item.title.take(256), children = visit(item.children, depth + 1))
                    is Block.Buttons -> item.copy(buttons = item.buttons.take(8).map { it.copy(label = it.label.take(256)) })
                    is Block.Unsupported -> item
                })
            }
            return result
        }
        return visit(blocks, 0)
    }
}
