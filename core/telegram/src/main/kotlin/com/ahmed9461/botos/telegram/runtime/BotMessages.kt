package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.*
import java.net.URI
import kotlinx.serialization.json.*

internal fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject
internal fun JsonObject.flag(name: String): Boolean = (get(name) as? JsonPrimitive)?.booleanOrNull == true
internal fun JsonObject.array(name: String): JsonArray = get(name) as? JsonArray ?: JsonArray(emptyList())

/** HTTP(S) only, without credentials. Every accepted link still requires an explicit UI confirmation. */
fun safeBotUrl(value: String): String? = try {
    val uri = URI(value)
    value.takeIf { it.length <= 4096 && uri.scheme?.lowercase() in setOf("https", "http") &&
        !uri.host.isNullOrBlank() && uri.rawUserInfo == null && value.none { c -> c.isISOControl() } }
} catch (_: Exception) { null }

internal object BotMessageAdapter {
    fun message(raw: JsonObject, key: ChatKey, revision: Long): BotMessage? {
        val id = raw.number("id")?.takeIf { it != 0L } ?: return null
        val content = raw.obj("content")
        val blocks = mutableListOf<Block>()
        if (content?.type() == "messageText") {
            blocks += Block.Paragraph("text", content.obj("text")?.string("text").orEmpty())
        } else {
            blocks += Block.Unsupported("content")
            content?.obj("caption")?.string("text")?.takeIf { it.isNotBlank() }?.let {
                blocks += Block.Paragraph("caption", it)
            }
        }
        val markup = raw.obj("reply_markup")
        if (markup?.type() == "replyMarkupInlineKeyboard") blocks += buttons(markup, inline = true)
        val outgoing = raw.flag("is_outgoing")
        val delivery = if (!outgoing) DeliveryState.NONE else when (raw.obj("sending_state")?.type()) {
            "messageSendingStatePending" -> DeliveryState.PENDING
            "messageSendingStateFailed" -> DeliveryState.FAILED
            null -> DeliveryState.SENT
            else -> DeliveryState.PENDING
        }
        return BotMessage(id, key, revision, ContentSafety.bound(blocks), outgoing, delivery)
    }

    fun keyboard(raw: JsonObject, key: ChatKey, revision: Long): BotMessage? {
        val markup = raw.obj("reply_markup") ?: return null
        if (markup.type() != "replyMarkupShowKeyboard") return null
        val id = raw.number("id")?.takeIf { it != 0L } ?: return null
        return BotMessage(id, key, revision, buttons(markup, inline = false))
    }

    private fun buttons(markup: JsonObject, inline: Boolean): List<Block> = markup.array("rows")
        .take(ContentLimits.MAX_BLOCKS - 2).mapIndexed { rowIndex, row ->
            val entries = (row as? JsonArray).orEmpty().take(8).mapIndexedNotNull { colIndex, element ->
                val button = element as? JsonObject ?: return@mapIndexedNotNull null
                val type = button.obj("type") ?: return@mapIndexedNotNull null
                val label = button.string("text").take(256)
                val payload = when {
                    inline && type.type() == "inlineKeyboardButtonTypeCallback" -> ActionPayload.Callback(type.string("data"))
                    inline && type.type() == "inlineKeyboardButtonTypeUrl" -> safeBotUrl(type.string("url"))?.let { ActionPayload.OpenUrl(it) } ?: ActionPayload.Unsupported
                    !inline && type.type() == "keyboardButtonTypeText" -> ActionPayload.SendText(button.string("text"))
                    else -> ActionPayload.Unsupported
                }
                BotButton("${if (inline) "inline" else "reply"}/$rowIndex/$colIndex", label, payload, payload != ActionPayload.Unsupported)
            }
            Block.Buttons("${if (inline) "inline" else "reply"}-$rowIndex", entries)
        }
}

/** Bounded per-view reducer. Revision and tombstone state never escape into another chat/account. */
internal class BotMessageReducer(val key: ChatKey, val chatId: Long) {
    private val raw = linkedMapOf<Long, JsonObject>()
    private val rendered = linkedMapOf<Long, BotMessage>()
    private val tombstones = linkedSetOf<Long>()
    private var revision = 0L
    var keyboard: BotMessage? = null; private set
    var keyboardOneTime = false; private set
    var keyboardVersion = 0L; private set
    fun timeline() = MessageTimeline(key, raw.keys.mapNotNull(rendered::get))

    fun add(message: JsonObject) {
        if (message.number("chat_id") != chatId) return
        val id = message.number("id") ?: return
        if (id == 0L || id in tombstones || raw[id] == message) return
        val model = BotMessageAdapter.message(message, key, ++revision) ?: return
        raw[id] = message
        rendered[id] = model
        while (raw.size > ContentLimits.MAX_MESSAGES) {
            val oldest = raw.keys.first(); raw.remove(oldest); rendered.remove(oldest)
        }
    }
    fun history(messages: List<JsonObject>) {
        val already = raw.toMap()
        val alreadyRendered = rendered.toMap()
        raw.clear(); rendered.clear()
        messages.forEach { message ->
            val id = message.number("id") ?: return@forEach
            if (id in already) { raw[id] = already.getValue(id); rendered[id] = alreadyRendered.getValue(id) }
            else add(message)
        }
        already.forEach { (id, value) -> if (id !in raw) { raw[id] = value; rendered[id] = alreadyRendered.getValue(id) } }
        while (raw.size > ContentLimits.MAX_MESSAGES) { val id = raw.keys.first(); raw.remove(id); rendered.remove(id) }
    }
    fun setKeyboard(message: JsonObject?) {
        keyboardVersion++
        keyboard = message?.takeIf { it.number("chat_id") == chatId }?.let { BotMessageAdapter.keyboard(it, key, ++revision) }
        keyboardOneTime = keyboard != null && message?.obj("reply_markup")?.flag("one_time") == true
    }
    fun resolve(ticket: ActionTicket): BotButton? = if (ticket.buttonId.startsWith("reply/")) {
        keyboard?.let { MessageTimeline(key, listOf(it)).resolve(ticket) }
    } else timeline().resolve(ticket)

    private fun forget(id: Long) {
        raw.remove(id); rendered.remove(id); tombstones.add(id)
        while (tombstones.size > 512) tombstones.remove(tombstones.first())
        if (keyboard?.id == id) setKeyboard(null)
    }
    fun update(update: JsonObject) {
        val message = update.obj("message")
        if ((message?.number("chat_id") ?: update.number("chat_id")) != chatId) return
        when (update.type()) {
            "updateNewMessage" -> message?.let(::add)
            "updateMessageSendSucceeded", "updateMessageSendFailed" -> {
                val oldId = update.number("old_message_id")
                if (oldId != null && message != null) {
                    val entries = raw.toList(); val finalId = message.number("id")
                    if (oldId != finalId) forget(oldId)
                    add(message)
                    if (finalId != null && entries.any { it.first == oldId }) {
                        val order = entries.map { if (it.first == oldId) finalId else it.first }.distinct()
                        val copy = raw.toMap(); raw.clear()
                        order.forEach { id -> copy[id]?.let { raw[id] = it } }
                        copy.forEach { (id, value) -> if (id !in raw) raw[id] = value }
                    }
                }
            }
            "updateMessageContent", "updateMessageEdited" -> {
                val id = update.number("message_id") ?: return
                val previous = raw[id] ?: return
                val replacement = previous.toMutableMap()
                if (update.type() == "updateMessageContent") replacement["content"] = update["new_content"] ?: JsonNull
                else replacement["reply_markup"] = update["reply_markup"] ?: JsonNull
                add(JsonObject(replacement))
                if (keyboard?.id == id) setKeyboard(JsonObject(replacement))
            }
            "updateDeleteMessages" -> if (update.flag("is_permanent")) {
                update.array("message_ids").mapNotNull { (it as? JsonPrimitive)?.longOrNull }.forEach(::forget)
            }
            "updateChatReplyMarkup" -> setKeyboard(update.obj("reply_markup_message"))
        }
    }
}
