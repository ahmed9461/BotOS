package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.*
import kotlinx.serialization.json.*

/** Transient bot output, not the user's draft and never a persisted message. */
data class PendingReply(val draftId: Long, val content: BotMessage, val canStop: Boolean,
    val keepOnStop: Boolean, val expiresAtMillis: Long, val stopped: Boolean = false)

internal class PendingReplies(private val chat: ChatKey, private val chatId: Long,
    private val periodMillis: Long, private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var revision = 0L
    var value: PendingReply? = null; private set
    fun update(update: JsonObject) {
        if (periodMillis <= 0 || update.number("chat_id") != chatId) return
        when (update.type()) {
            "updatePendingMessage" -> {
                if ((update.number("forum_topic_id") ?: 0L) != 0L) return
                val id = update.number("draft_id") ?: return
                val content = update.obj("content") ?: return
                if (content.type() !in setOf("messageText", "messageRichMessage")) return
                val raw = TdJson.command("message") {
                    put("id", Long.MIN_VALUE); put("chat_id", chatId); put("content", content)
                }
                val message = BotMessageAdapter.message(raw, chat, ++revision) ?: return
                value = PendingReply(id, message.copy(blocks = readonly(message.blocks), inlineActions = emptyList()),
                    update.flag("can_stop"), update.flag("keep_on_stop"), clock() + periodMillis.coerceAtMost(120_000))
            }
            "updateStopMessageDraft" -> if ((update.number("forum_topic_id") ?: 0L) == 0L) {
                update.number("draft_id")?.let(::stop)
            }
        }
    }
    fun incoming(message: JsonObject) {
        if (message.number("chat_id") != chatId || message.flag("is_outgoing")) return
        if (message.obj("topic_id") != null) return
        value = null
    }
    fun stop(draftId: Long) {
        val old = value?.takeIf { it.draftId == draftId } ?: return
        value = if (old.keepOnStop) old.copy(stopped = true, canStop = false) else null
    }
    fun expire(): Boolean {
        val old = value ?: return false
        if (clock() < old.expiresAtMillis) return false
        value = null
        return true
    }
    private fun readonly(blocks: List<Block>): List<Block> = blocks.map { block -> when (block) {
        is Block.Buttons -> block.copy(buttons = block.buttons.map { it.copy(enabled = false, payload = ActionPayload.Unsupported) })
        is Block.Details -> block.copy(children = readonly(block.children))
        is Block.RichDetails -> block.copy(children = readonly(block.children))
        is Block.RichQuote -> block.copy(children = readonly(block.children))
        is Block.Gallery -> block.copy(children = readonly(block.children))
        is Block.RichList -> block.copy(items = block.items.map { it.copy(blocks = readonly(it.blocks)) })
        else -> block
    } }
}
