package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.*
import java.net.URI
import kotlinx.serialization.json.*

internal fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject
internal fun JsonObject.flag(name: String): Boolean = (get(name) as? JsonPrimitive)?.booleanOrNull == true
internal fun JsonObject.array(name: String): JsonArray = get(name) as? JsonArray ?: JsonArray(emptyList())
internal fun JsonObject.decimal(name: String): Double? = (get(name) as? JsonPrimitive)?.doubleOrNull

/** HTTP(S) only, without credentials. Accepted links still require explicit user confirmation. */
fun safeBotUrl(value: String): String? = try {
    val uri = URI(value)
    value.takeIf { it.length <= 4096 && uri.scheme?.lowercase() in setOf("https", "http") &&
        !uri.host.isNullOrBlank() && uri.rawUserInfo == null && value.none { c -> c.isISOControl() } }
} catch (_: Exception) { null }

private fun concatStyled(parts: Iterable<StyledText>) = StyledText(parts.flatMap { it.spans })

internal object BotMessageAdapter {
    fun message(raw: JsonObject, key: ChatKey, revision: Long): BotMessage? {
        val id = raw.number("id")?.takeIf { it != 0L } ?: return null
        val content = raw.obj("content")
        val adapter = RichAdapter()
        val rich = content?.takeIf { it.type() == "messageRichMessage" }?.obj("message")
        val protected = raw.obj("self_destruct_type") != null || (raw.decimal("self_destruct_in") ?: 0.0) > 0.0
        val blocks = (if (protected) listOf(Block.Unsupported("protected-media")) else when (content?.type()) {
            "messageText" -> listOf(Block.Paragraph("text", content.obj("text")?.string("text").orEmpty()))
            "messageRichMessage" -> adapter.blocks(rich?.array("blocks") ?: JsonArray(emptyList()), "rich", 0)
                .ifEmpty { listOf(Block.Unsupported("rich-empty")) }
            else -> content?.let(StandardMediaAdapter::blocks) ?: buildList {
                add(Block.Unsupported("content"))
                content?.obj("caption")?.string("text")?.takeIf { it.isNotBlank() }?.let { add(Block.Paragraph("caption", it)) }
            }
        }).toMutableList()
        val markup = raw.obj("reply_markup")
        if (markup?.type() == "replyMarkupInlineKeyboard") blocks += buttons(markup, inline = true)
        val outgoing = raw.flag("is_outgoing")
        val delivery = if (!outgoing) DeliveryState.NONE else when (raw.obj("sending_state")?.type()) {
            "messageSendingStatePending" -> DeliveryState.PENDING
            "messageSendingStateFailed" -> DeliveryState.FAILED
            null -> DeliveryState.SENT
            else -> DeliveryState.PENDING
        }
        return BotMessage(id, key, revision, ContentSafety.bound(blocks), outgoing, delivery,
            date = raw.number("date") ?: 0L, forceRtl = rich?.flag("is_rtl"),
            isFull = rich?.flag("is_full") ?: true, inlineActions = adapter.actions.toList())
    }

    fun keyboard(raw: JsonObject, key: ChatKey, revision: Long): BotMessage? {
        val markup = raw.obj("reply_markup") ?: return null
        if (markup.type() != "replyMarkupShowKeyboard") return null
        val id = raw.number("id")?.takeIf { it != 0L } ?: return null
        return BotMessage(id, key, revision, buttons(markup, inline = false), date = raw.number("date") ?: 0L)
    }
    private fun buttonPayload(type: JsonObject, inline: Boolean, fallbackText: String): ActionPayload = when {
        inline && type.type() == "inlineKeyboardButtonTypeCallback" -> ActionPayload.Callback(type.string("data"))
        inline && type.type() == "inlineKeyboardButtonTypeUrl" -> safeBotUrl(type.string("url"))?.let(ActionPayload::OpenUrl) ?: ActionPayload.Unsupported
        !inline && type.type() == "keyboardButtonTypeText" -> ActionPayload.SendText(fallbackText)
        else -> ActionPayload.Unsupported
    }
    private fun buttonStyle(style: JsonObject?): ButtonVisualStyle = when (style?.type()) {
        "buttonStylePrimary" -> ButtonVisualStyle.PRIMARY
        "buttonStyleDanger" -> ButtonVisualStyle.DANGER
        "buttonStyleSuccess" -> ButtonVisualStyle.SUCCESS
        "buttonStyleLink" -> ButtonVisualStyle.LINK
        else -> ButtonVisualStyle.DEFAULT
    }
    private fun buttons(markup: JsonObject, inline: Boolean): List<Block> {
        val prefix = if (inline) "inline" else "reply"
        return markup.array("rows").take(ContentLimits.MAX_BLOCKS - 2).mapIndexed { rowIndex, row ->
            val entries = (row as? JsonArray).orEmpty().take(8).mapIndexedNotNull { colIndex, element ->
                val button = element as? JsonObject ?: return@mapIndexedNotNull null
                val type = button.obj("type") ?: return@mapIndexedNotNull null
                val label = button.string("text").take(256)
                val payload = buttonPayload(type, inline, button.string("text"))
                BotButton("$prefix/$rowIndex/$colIndex", label, payload,
                    payload != ActionPayload.Unsupported, buttonStyle(button.obj("style")))
            }
            Block.Buttons("$prefix-$rowIndex", entries)
        }
    }

    /** Parse within shared budgets; do not first allocate an unbounded recursive tree. */
    private class RichAdapter {
        val actions = mutableListOf<BotButton>()
        private var remainingBlocks = ContentLimits.MAX_BLOCKS
        private var remainingRichNodes = 4096
        private var remainingText = ContentLimits.MAX_TEXT
        private var richDepth = 0
        private var actionSequence = 0
        fun blocks(items: JsonArray, path: String, depth: Int): List<Block> {
            if (depth >= ContentLimits.MAX_DEPTH) return listOf(Block.Unsupported("$path-depth"))
            val result = mutableListOf<Block>()
            for ((index, element) in items.withIndex()) {
                if (remainingBlocks <= 0) { result += Block.Unsupported("$path-limit"); break }
                val item = element as? JsonObject
                if (item == null) { remainingBlocks--; result += Block.Unsupported("$path-$index"); continue }
                result += block(item, "$path-$index", depth)
            }
            return result
        }
        private fun leaf(value: String, marks: Set<RichMark>): StyledText {
            var text = value.take(remainingText)
            if (text.lastOrNull()?.isHighSurrogate() == true) text = text.dropLast(1)
            remainingText -= text.length
            return if (text.isEmpty()) StyledText.EMPTY else StyledText(listOf(StyledSpan(text, marks)))
        }
        private fun link(text: StyledText, rawUrl: String): StyledText {
            val url = safeBotUrl(rawUrl) ?: return text
            if (actions.size >= 256 || text.isBlank()) return text
            val id = "rich-link/${actionSequence++}"
            actions += BotButton(id, text.plainText().take(256), ActionPayload.OpenUrl(url))
            return StyledText(text.spans.map { it.copy(url = url, actionId = id) })
        }
        private fun block(raw: JsonObject, id: String, depth: Int): List<Block> {
            if (remainingBlocks-- <= 0 || depth >= ContentLimits.MAX_DEPTH) return listOf(Block.Unsupported("$id-limit"))
            return when (raw.type()) {
                "pageBlockTitle" -> listOf(Block.Heading(id, rich(raw.obj("title")), 1))
                "pageBlockSubtitle" -> listOf(Block.Heading(id, rich(raw.obj("subtitle")), 2))
                "pageBlockAuthorDate" -> listOf(Block.Paragraph(id, rich(raw.obj("author"))))
                "pageBlockHeader" -> listOf(Block.Heading(id, rich(raw.obj("header")), 2))
                "pageBlockSubheader" -> listOf(Block.Heading(id, rich(raw.obj("subheader")), 3))
                "pageBlockSectionHeading" -> listOf(Block.Heading(id, rich(raw.obj("text")), (raw.number("size") ?: 3L).coerceIn(1, 6).toInt()))
                "pageBlockKicker" -> listOf(Block.Heading(id, rich(raw.obj("kicker")), 4))
                "pageBlockParagraph" -> listOf(Block.Paragraph(id, rich(raw.obj("text"))))
                "pageBlockPreformatted" -> listOf(Block.Code(id, rich(raw.obj("text")), raw.string("language")))
                "pageBlockFooter" -> listOf(Block.Paragraph(id, rich(raw.obj("footer"))))
                "pageBlockThinking" -> listOf(Block.Quote(id, rich(raw.obj("text"))))
                "pageBlockDivider" -> listOf(Block.Divider(id))
                "pageBlockMathematicalExpression" -> listOf(Block.Math(id, leaf(raw.string("expression"), emptySet()).plainText()))
                "pageBlockAnchor" -> emptyList()
                "pageBlockList" -> listOf(Block.RichList(id, raw.array("items").take(100).mapIndexedNotNull { index, element ->
                    val item = element as? JsonObject ?: return@mapIndexedNotNull null
                    RichListItem(item.string("label"), blocks(item.array("blocks"), "$id-item-$index", depth + 1),
                        if (item.flag("has_checkbox")) item.flag("is_checked") else null)
                }))
                "pageBlockBlockQuote" -> listOf(Block.RichQuote(id, blocks(raw.array("blocks"), "$id-quote", depth + 1), rich(raw.obj("credit"))))
                "pageBlockExpandableBlockQuote" -> listOf(Block.Quote(id, rich(raw.obj("text")), rich(raw.obj("credit")).takeUnless(StyledText::isBlank), expandable = true))
                "pageBlockPullQuote" -> listOf(Block.Quote(id, rich(raw.obj("text")), rich(raw.obj("credit")).takeUnless(StyledText::isBlank)))
                "pageBlockAnimation" -> protectedMedia(raw, id, fileInfo(raw, "animation", "animation", MediaKind.ANIMATION))
                "pageBlockAudio" -> listOf(Block.Media(id, fileInfo(raw, "audio", "audio", MediaKind.AUDIO)))
                "pageBlockDocument" -> listOf(Block.Media(id, fileInfo(raw, "document", "document", MediaKind.DOCUMENT)))
                "pageBlockVideo" -> protectedMedia(raw, id, fileInfo(raw, "video", "video", MediaKind.VIDEO))
                "pageBlockVoiceNote" -> listOf(Block.Media(id, fileInfo(raw, "voice_note", "voice", MediaKind.VOICE_NOTE)))
                "pageBlockPhoto" -> protectedMedia(raw, id, photoInfo(raw))
                "pageBlockCover" -> raw.obj("cover")?.let { block(it, "$id-cover", depth + 1) } ?: listOf(Block.Unsupported(id))
                "pageBlockEmbedded" -> listOf(Block.Media(id, MediaInfo(MediaKind.EMBEDDED,
                    caption = caption(raw.obj("caption")), url = safeBotUrl(raw.string("url")),
                    width = raw.number("width")?.toInt(), height = raw.number("height")?.toInt())))
                "pageBlockEmbeddedPost" -> buildList {
                    raw.string("author").takeIf(String::isNotBlank)?.let { add(Block.Heading("$id-author", leaf(it, emptySet()))) }
                    addAll(blocks(raw.array("blocks"), "$id-post", depth + 1))
                    caption(raw.obj("caption")).takeUnless(StyledText::isBlank)?.let { add(Block.Paragraph("$id-caption", it)) }
                }
                "pageBlockCollage", "pageBlockSlideshow" -> listOf(Block.Gallery(id,
                    blocks(raw.array("blocks"), "$id-media", depth + 1), caption(raw.obj("caption")), raw.type() == "pageBlockSlideshow"))
                "pageBlockChatLink" -> listOf(Block.Media(id, MediaInfo(MediaKind.CHAT_LINK,
                    title = raw.string("title").ifBlank { raw.string("username") },
                    url = raw.string("username").takeIf(String::isNotBlank)?.let { safeBotUrl("https://t.me/$it") })))
                "pageBlockTable" -> listOf(Block.RichTable(id, rich(raw.obj("caption")),
                    rows = raw.array("cells").take(ContentLimits.MAX_ROWS).map { row ->
                        (row as? JsonArray).orEmpty().take(ContentLimits.MAX_COLUMNS).map { element ->
                            val cell = element as? JsonObject
                            RichTableCell(rich(cell?.obj("text")), cell?.flag("is_header") == true,
                                (cell?.number("colspan") ?: 1L).coerceIn(1, ContentLimits.MAX_COLUMNS.toLong()).toInt(),
                                (cell?.number("rowspan") ?: 1L).coerceIn(1, ContentLimits.MAX_ROWS.toLong()).toInt(),
                                invisible = cell?.obj("text") == null)
                        }
                    }, compact = raw.flag("is_compact")))
                "pageBlockDetails" -> listOf(Block.RichDetails(id, rich(raw.obj("header")), blocks(raw.array("blocks"), "$id-details", depth + 1), raw.flag("is_open")))
                "pageBlockRelatedArticles" -> listOf(Block.Bullets(id, raw.array("articles").take(50).mapNotNull { element ->
                    val article = element as? JsonObject ?: return@mapNotNull null
                    leaf(listOf(article.string("title"), article.string("description"), article.string("url"))
                        .filter(String::isNotBlank).joinToString(" — ").take(512), emptySet()).plainText()
                }))
                "pageBlockMap" -> listOf(Block.Media(id, MediaInfo(MediaKind.MAP,
                    caption = caption(raw.obj("caption")), latitude = raw.obj("location")?.decimal("latitude"), longitude = raw.obj("location")?.decimal("longitude"))))
                "pageBlockButtonRow" -> listOf(Block.Buttons(id, raw.array("buttons").take(8).mapIndexedNotNull { index, element ->
                    richButton(element as? JsonObject ?: return@mapIndexedNotNull null, "$id/$index")
                }))
                else -> listOf(Block.Unsupported(id))
            }
        }
        private fun protectedMedia(raw: JsonObject, id: String, info: MediaInfo): List<Block> {
            val media = Block.Media(id, info)
            return if (raw.flag("has_spoiler")) {
                listOf(Block.Details("$id-spoiler", "•••", listOf(media)))
            } else listOf(media)
        }

        private fun richButton(button: JsonObject, id: String): BotButton? {
            if (button.type() != "inlineButton") return null
            val label = rich(button.obj("text")).plainText().take(256)
            val type = button.obj("type") ?: return BotButton(id, label, ActionPayload.Unsupported, false)
            val payload = buttonPayload(type, inline = true, fallbackText = label)
            return BotButton(id, label, payload, payload != ActionPayload.Unsupported, buttonStyle(button.obj("style")))
        }
        private fun rich(raw: JsonObject?, inherited: Set<RichMark> = emptySet()): StyledText {
            raw ?: return StyledText.EMPTY
            if (richDepth >= ContentLimits.MAX_DEPTH || remainingRichNodes-- <= 0 || remainingText <= 0) return StyledText.EMPTY
            richDepth++
            try {
                fun child(mark: RichMark? = null) = rich(raw.obj("text"), if (mark == null) inherited else inherited + mark)
                return when (raw.type()) {
                    "richTextPlain" -> leaf(raw.string("text"), inherited)
                    "richTextBold" -> child(RichMark.BOLD)
                    "richTextItalic" -> child(RichMark.ITALIC)
                    "richTextUnderline" -> child(RichMark.UNDERLINE)
                    "richTextStrikethrough" -> child(RichMark.STRIKETHROUGH)
                    "richTextSpoiler" -> child(RichMark.SPOILER)
                    "richTextSubscript" -> child(RichMark.SUBSCRIPT)
                    "richTextSuperscript" -> child(RichMark.SUPERSCRIPT)
                    "richTextMarked" -> child(RichMark.MARKED)
                    "richTextFixed" -> child(RichMark.CODE)
                    "richTextMathematicalExpression" -> leaf(raw.string("expression"), inherited + RichMark.MATH)
                    "richTextUrl", "richTextReferenceLink", "richTextAnchorLink" -> link(child(), raw.string("url"))
                    "richTextCustomEmoji" -> leaf(raw.string("alternative_text"), inherited)
                    "richTextIcon" -> leaf("◉", inherited)
                    "richTextButton" -> {
                        val id = "rich-inline/${actionSequence++}"
                        val button = raw.obj("button")?.let { richButton(it, id) }
                        if (button == null) StyledText.EMPTY else {
                            if (actions.size < 256) actions += button
                            StyledText(listOf(StyledSpan(button.label, inherited + RichMark.UNDERLINE,
                                actionId = id.takeIf { button.enabled && actions.any { action -> action.id == id } })))
                        }
                    }
                    "richTextDiff" -> concatStyled(listOf(rich(raw.obj("old_text"), inherited + RichMark.STRIKETHROUGH), child()))
                    "richTextReference", "richTextDateTime", "richTextMention", "richTextHashtag", "richTextCashtag",
                    "richTextBankCardNumber", "richTextBotCommand", "richTextMentionName", "richTextEmailAddress", "richTextPhoneNumber" -> child()
                    "richTexts" -> concatStyled(raw.array("texts").take(4096).mapNotNull { it as? JsonObject }.map { rich(it, inherited) })
                    "richTextAnchor" -> StyledText.EMPTY
                    else -> raw.string("text").takeIf(String::isNotBlank)?.let { leaf(it, inherited) } ?: StyledText.EMPTY
                }
            } finally { richDepth-- }
        }
        private fun caption(raw: JsonObject?): StyledText {
            raw ?: return StyledText.EMPTY
            val text = rich(raw.obj("text"))
            val credit = rich(raw.obj("credit"))
            return if (credit.isBlank()) text else concatStyled(listOf(text, StyledText.plain(if (text.isBlank()) "" else "\n"), credit))
        }
        private fun fileInfo(raw: JsonObject, field: String, fileField: String, kind: MediaKind): MediaInfo {
            val media = raw.obj(field)
            val file = media?.obj(fileField)
            val title = if (kind == MediaKind.AUDIO) listOf(media?.string("title").orEmpty(), media?.string("performer").orEmpty())
                .filter(String::isNotBlank).joinToString(" — ") else media?.string("file_name").orEmpty()
            return MediaInfo(kind, title = title, caption = caption(raw.obj("caption")), fileId = file?.number("id")?.toInt(),
                fileName = media?.string("file_name").orEmpty(), mimeType = media?.string("mime_type").orEmpty(),
                size = file?.number("size"), durationSeconds = media?.number("duration")?.toInt(),
                width = media?.number("width")?.toInt(), height = media?.number("height")?.toInt())
        }
        private fun photoInfo(raw: JsonObject): MediaInfo {
            val best = raw.obj("photo")?.array("sizes")?.mapNotNull { it as? JsonObject }
                ?.maxByOrNull { (it.number("width") ?: 0L) * (it.number("height") ?: 0L) }
            val file = best?.obj("photo")
            return MediaInfo(MediaKind.PHOTO, caption = caption(raw.obj("caption")), fileId = file?.number("id")?.toInt(),
                size = file?.number("size"), width = best?.number("width")?.toInt(), height = best?.number("height")?.toInt(), url = safeBotUrl(raw.string("url")))
        }
    }
}

/** Bounded per-view reducer. Revisions and tombstones never escape into another account/chat. */
internal class BotMessageReducer(val key: ChatKey, val chatId: Long) {
    private val raw = linkedMapOf<Long, JsonObject>()
    private val rendered = linkedMapOf<Long, BotMessage>()
    private val tombstones = linkedSetOf<Long>()
    private var revision = 0L
    var keyboard: BotMessage? = null; private set
    var keyboardOneTime = false; private set
    var keyboardVersion = 0L; private set
    fun timeline() = MessageTimeline(key, raw.keys.mapNotNull(rendered::get))
    fun incompleteRich(): List<BotMessage> = rendered.values.filter { !it.isFull }
    fun completeRich(id: Long, expectedRevision: Long, full: JsonObject): Boolean {
        val previous = raw[id] ?: return false
        if (rendered[id]?.revision != expectedRevision || full.type() != "richMessage" || !full.flag("is_full")) return false
        if (previous.obj("content")?.type() != "messageRichMessage") return false
        val replacement = previous.toMutableMap()
        replacement["content"] = TdJson.command("messageRichMessage") { put("message", full) }
        add(JsonObject(replacement))
        return true
    }
    fun add(message: JsonObject) {
        if (message.number("chat_id") != chatId) return
        val id = message.number("id") ?: return
        if (id == 0L || id in tombstones || raw[id] == message) return
        val model = BotMessageAdapter.message(message, key, ++revision) ?: return
        raw[id] = message; rendered[id] = model
        while (raw.size > ContentLimits.MAX_MESSAGES) { val id = raw.keys.first(); raw.remove(id); rendered.remove(id) }
    }
    fun history(messages: List<JsonObject>) {
        val already = raw.toMap(); val alreadyRendered = rendered.toMap()
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
