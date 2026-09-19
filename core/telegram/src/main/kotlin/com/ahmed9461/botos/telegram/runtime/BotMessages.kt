package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.*
import java.net.URI
import kotlinx.serialization.json.*

internal fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject
internal fun JsonObject.flag(name: String): Boolean = (get(name) as? JsonPrimitive)?.booleanOrNull == true
internal fun JsonObject.array(name: String): JsonArray = get(name) as? JsonArray ?: JsonArray(emptyList())
internal fun JsonObject.decimal(name: String): Double? = (get(name) as? JsonPrimitive)?.doubleOrNull

/** HTTP(S) only, without credentials. Every accepted link still requires an explicit UI confirmation. */
fun safeBotUrl(value: String): String? = try {
    val uri = URI(value)
    value.takeIf {
        it.length <= 4096 && uri.scheme?.lowercase() in setOf("https", "http") &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null && value.none { c -> c.isISOControl() }
    }
} catch (_: Exception) {
    null
}

private fun StyledText.withUrl(url: String?) = if (url == null) this else StyledText(spans.map { it.copy(url = url) })
private fun concatStyled(parts: Iterable<StyledText>) = StyledText(parts.flatMap { it.spans })

internal object BotMessageAdapter {
    fun message(raw: JsonObject, key: ChatKey, revision: Long): BotMessage? {
        val id = raw.number("id")?.takeIf { it != 0L } ?: return null
        val content = raw.obj("content")
        val rich = content?.takeIf { it.type() == "messageRichMessage" }?.obj("message")
        val blocks = when (content?.type()) {
            "messageText" -> listOf(Block.Paragraph("text", content.obj("text")?.string("text").orEmpty()))
            "messageRichMessage" -> RichAdapter().blocks(rich?.array("blocks") ?: JsonArray(emptyList()), "rich", 0)
                .ifEmpty { listOf(Block.Unsupported("rich-empty")) }
            else -> buildList {
                add(Block.Unsupported("content"))
                content?.obj("caption")?.string("text")?.takeIf { it.isNotBlank() }?.let {
                    add(Block.Paragraph("caption", it))
                }
            }
        }.toMutableList()

        val markup = raw.obj("reply_markup")
        if (markup?.type() == "replyMarkupInlineKeyboard") blocks += buttons(markup, inline = true)

        val outgoing = raw.flag("is_outgoing")
        val delivery = if (!outgoing) DeliveryState.NONE else when (raw.obj("sending_state")?.type()) {
            "messageSendingStatePending" -> DeliveryState.PENDING
            "messageSendingStateFailed" -> DeliveryState.FAILED
            null -> DeliveryState.SENT
            else -> DeliveryState.PENDING
        }
        return BotMessage(
            id = id,
            chat = key,
            revision = revision,
            blocks = ContentSafety.bound(blocks),
            outgoing = outgoing,
            delivery = delivery,
            date = raw.number("date") ?: 0L,
            forceRtl = rich?.flag("is_rtl"),
        )
    }

    fun keyboard(raw: JsonObject, key: ChatKey, revision: Long): BotMessage? {
        val markup = raw.obj("reply_markup") ?: return null
        if (markup.type() != "replyMarkupShowKeyboard") return null
        val id = raw.number("id")?.takeIf { it != 0L } ?: return null
        return BotMessage(id, key, revision, buttons(markup, inline = false), date = raw.number("date") ?: 0L)
    }

    private fun buttonPayload(type: JsonObject, inline: Boolean, fallbackText: String): ActionPayload = when {
        inline && type.type() == "inlineKeyboardButtonTypeCallback" -> ActionPayload.Callback(type.string("data"))
        inline && type.type() == "inlineKeyboardButtonTypeUrl" ->
            safeBotUrl(type.string("url"))?.let(ActionPayload::OpenUrl) ?: ActionPayload.Unsupported
        !inline && type.type() == "keyboardButtonTypeText" -> ActionPayload.SendText(fallbackText)
        else -> ActionPayload.Unsupported
    }

    private fun buttons(markup: JsonObject, inline: Boolean): List<Block> {
        val prefix = if (inline) "inline" else "reply"
        return markup.array("rows")
            .take(ContentLimits.MAX_BLOCKS - 2)
            .mapIndexed { rowIndex, row ->
                val entries = (row as? JsonArray).orEmpty().take(8).mapIndexedNotNull { colIndex, element ->
                    val button = element as? JsonObject ?: return@mapIndexedNotNull null
                    val type = button.obj("type") ?: return@mapIndexedNotNull null
                    val label = button.string("text").take(256)
                    val payload = buttonPayload(type, inline, label)
                    BotButton(
                        id = "$prefix/$rowIndex/$colIndex",
                        label = label,
                        payload = payload,
                        enabled = payload != ActionPayload.Unsupported,
                    )
                }
                Block.Buttons("$prefix-$rowIndex", entries)
            }
    }

    private class RichAdapter {
        fun blocks(items: JsonArray, path: String, depth: Int): List<Block> {
            if (depth >= ContentLimits.MAX_DEPTH) return listOf(Block.Unsupported("$path-depth"))
            return items.take(ContentLimits.MAX_BLOCKS).flatMapIndexed { index, element ->
                val block = element as? JsonObject ?: return@flatMapIndexed listOf(Block.Unsupported("$path-$index"))
                block(block, "$path-$index", depth)
            }
        }

        private fun block(raw: JsonObject, id: String, depth: Int): List<Block> = when (raw.type()) {
            "pageBlockTitle" -> listOf(Block.Heading(id, rich(raw.obj("title")), 1))
            "pageBlockSubtitle" -> listOf(Block.Heading(id, rich(raw.obj("subtitle")), 2))
            "pageBlockAuthorDate" -> listOf(Block.Paragraph(id, rich(raw.obj("author"))))
            "pageBlockHeader" -> listOf(Block.Heading(id, rich(raw.obj("header")), 2))
            "pageBlockSubheader" -> listOf(Block.Heading(id, rich(raw.obj("subheader")), 3))
            "pageBlockSectionHeading" -> listOf(Block.Heading(id, rich(raw.obj("text")), (raw.number("size") ?: 3L).toInt().coerceIn(1, 6)))
            "pageBlockKicker" -> listOf(Block.Heading(id, rich(raw.obj("kicker")), 4))
            "pageBlockParagraph" -> listOf(Block.Paragraph(id, rich(raw.obj("text"))))
            "pageBlockPreformatted" -> listOf(Block.Code(id, rich(raw.obj("text")), raw.string("language")))
            "pageBlockFooter" -> listOf(Block.Paragraph(id, rich(raw.obj("footer"))))
            "pageBlockThinking" -> listOf(Block.Quote(id, rich(raw.obj("text"))))
            "pageBlockDivider" -> listOf(Block.Divider(id))
            "pageBlockMathematicalExpression" -> listOf(Block.Math(id, raw.string("expression")))
            "pageBlockAnchor" -> emptyList()
            "pageBlockList" -> listOf(
                Block.RichList(
                    id,
                    raw.array("items").take(100).mapIndexedNotNull { itemIndex, itemElement ->
                        val item = itemElement as? JsonObject ?: return@mapIndexedNotNull null
                        val hasCheckbox = item.flag("has_checkbox")
                        RichListItem(
                            label = item.string("label"),
                            blocks = blocks(item.array("blocks"), "$id-item-$itemIndex", depth + 1),
                            checked = if (hasCheckbox) item.flag("is_checked") else null,
                        )
                    },
                ),
            )
            "pageBlockBlockQuote" -> listOf(
                Block.Quote(
                    id,
                    StyledText.plain(plainBlocks(raw.array("blocks"), depth + 1)),
                    rich(raw.obj("credit")).takeUnless(StyledText::isBlank),
                ),
            )
            "pageBlockExpandableBlockQuote" -> listOf(
                Block.Quote(
                    id,
                    rich(raw.obj("text")),
                    rich(raw.obj("credit")).takeUnless(StyledText::isBlank),
                    expandable = true,
                ),
            )
            "pageBlockPullQuote" -> listOf(
                Block.Quote(
                    id,
                    rich(raw.obj("text")),
                    rich(raw.obj("credit")).takeUnless(StyledText::isBlank),
                ),
            )
            "pageBlockAnimation" -> listOf(Block.Media(id, animationInfo(raw)))
            "pageBlockAudio" -> listOf(Block.Media(id, audioInfo(raw)))
            "pageBlockDocument" -> listOf(Block.Media(id, documentInfo(raw)))
            "pageBlockPhoto" -> listOf(Block.Media(id, photoInfo(raw)))
            "pageBlockVideo" -> listOf(Block.Media(id, videoInfo(raw)))
            "pageBlockVoiceNote" -> listOf(Block.Media(id, voiceInfo(raw)))
            "pageBlockCover" -> raw.obj("cover")?.let { block(it, "$id-cover", depth + 1) } ?: listOf(Block.Unsupported(id))
            "pageBlockEmbedded" -> listOf(
                Block.Media(
                    id,
                    MediaInfo(
                        kind = MediaKind.EMBEDDED,
                        title = "Embedded",
                        caption = caption(raw.obj("caption")),
                        url = safeBotUrl(raw.string("url")),
                        width = raw.number("width")?.toInt(),
                        height = raw.number("height")?.toInt(),
                    ),
                ),
            )
            "pageBlockEmbeddedPost" -> buildList {
                raw.string("author").takeIf(String::isNotBlank)?.let { add(Block.Heading("$id-author", it)) }
                addAll(blocks(raw.array("blocks"), "$id-post", depth + 1))
                caption(raw.obj("caption")).takeUnless(StyledText::isBlank)?.let { add(Block.Paragraph("$id-caption", it)) }
            }
            "pageBlockCollage", "pageBlockSlideshow" -> listOf(
                Block.Gallery(
                    id = id,
                    children = blocks(raw.array("blocks"), "$id-media", depth + 1),
                    caption = caption(raw.obj("caption")),
                    slideshow = raw.type() == "pageBlockSlideshow",
                ),
            )
            "pageBlockChatLink" -> listOf(
                Block.Media(
                    id,
                    MediaInfo(
                        kind = MediaKind.CHAT_LINK,
                        title = raw.string("title").ifBlank { raw.string("username") },
                        url = raw.string("username").takeIf(String::isNotBlank)?.let { safeBotUrl("https://t.me/$it") },
                    ),
                ),
            )
            "pageBlockTable" -> listOf(
                Block.RichTable(
                    id = id,
                    caption = rich(raw.obj("caption")),
                    rows = raw.array("cells").take(ContentLimits.MAX_ROWS).map { rowElement ->
                        (rowElement as? JsonArray).orEmpty().take(ContentLimits.MAX_COLUMNS).mapNotNull { cellElement ->
                            val cell = cellElement as? JsonObject ?: return@mapNotNull null
                            RichTableCell(
                                content = rich(cell.obj("text")),
                                isHeader = cell.flag("is_header"),
                                colspan = (cell.number("colspan") ?: 1L).toInt(),
                                rowspan = (cell.number("rowspan") ?: 1L).toInt(),
                            )
                        }
                    },
                    compact = raw.flag("is_compact"),
                ),
            )
            "pageBlockDetails" -> listOf(
                Block.RichDetails(
                    id = id,
                    header = rich(raw.obj("header")),
                    children = blocks(raw.array("blocks"), "$id-details", depth + 1),
                    initiallyOpen = raw.flag("is_open"),
                ),
            )
            "pageBlockRelatedArticles" -> listOf(
                Block.Bullets(
                    id,
                    raw.array("articles").take(50).mapNotNull { element ->
                        val article = element as? JsonObject ?: return@mapNotNull null
                        listOf(article.string("title"), article.string("description"), article.string("url"))
                            .filter(String::isNotBlank).joinToString(" — ").take(512)
                    },
                ),
            )
            "pageBlockMap" -> {
                val location = raw.obj("location")
                listOf(
                    Block.Media(
                        id,
                        MediaInfo(
                            kind = MediaKind.MAP,
                            title = "Map",
                            caption = caption(raw.obj("caption")),
                            latitude = location?.decimal("latitude"),
                            longitude = location?.decimal("longitude"),
                        ),
                    ),
                )
            }
            "pageBlockButtonRow" -> listOf(
                Block.Buttons(
                    id,
                    raw.array("buttons").take(8).mapIndexedNotNull { index, element ->
                        richButton(element as? JsonObject ?: return@mapIndexedNotNull null, "$id/$index")
                    },
                ),
            )
            "pageBlockUnsupported" -> listOf(Block.Unsupported(id))
            else -> listOf(Block.Unsupported(id))
        }

        private fun richButton(button: JsonObject, id: String): BotButton? {
            if (button.type() != "inlineButton") return null
            val label = rich(button.obj("text")).plainText().take(256)
            val type = button.obj("type") ?: return BotButton(id, label, ActionPayload.Unsupported, false)
            val payload = buttonPayload(type, inline = true, fallbackText = label)
            val style = when (button.obj("style")?.type()) {
                "buttonStylePrimary" -> ButtonVisualStyle.PRIMARY
                "buttonStyleDanger" -> ButtonVisualStyle.DANGER
                "buttonStyleSuccess" -> ButtonVisualStyle.SUCCESS
                "buttonStyleLink" -> ButtonVisualStyle.LINK
                else -> ButtonVisualStyle.DEFAULT
            }
            return BotButton(id, label, payload, payload != ActionPayload.Unsupported, style)
        }

        private fun rich(raw: JsonObject?, inherited: Set<RichMark> = emptySet()): StyledText {
            raw ?: return StyledText.EMPTY
            fun child(mark: RichMark? = null): StyledText {
                val next = if (mark == null) inherited else inherited + mark
                return rich(raw.obj("text"), next)
            }
            return when (raw.type()) {
                "richTextPlain" -> StyledText(listOf(StyledSpan(raw.string("text"), inherited)))
                "richTextBold" -> child(RichMark.BOLD)
                "richTextItalic" -> child(RichMark.ITALIC)
                "richTextUnderline" -> child(RichMark.UNDERLINE)
                "richTextStrikethrough" -> child(RichMark.STRIKETHROUGH)
                "richTextSpoiler" -> child(RichMark.SPOILER)
                "richTextSubscript" -> child(RichMark.SUBSCRIPT)
                "richTextSuperscript" -> child(RichMark.SUPERSCRIPT)
                "richTextMarked" -> child(RichMark.MARKED)
                "richTextFixed" -> child(RichMark.CODE)
                "richTextMathematicalExpression" ->
                    StyledText(listOf(StyledSpan(raw.string("expression"), inherited + RichMark.MATH)))
                "richTextUrl" -> child().withUrl(safeBotUrl(raw.string("url")))
                "richTextReferenceLink" -> child().withUrl(safeBotUrl(raw.string("url")))
                "richTextAnchorLink" -> child().withUrl(safeBotUrl(raw.string("url")))
                "richTextCustomEmoji" -> StyledText(listOf(StyledSpan(raw.string("alternative_text"), inherited)))
                "richTextIcon" -> StyledText(listOf(StyledSpan("◉", inherited)))
                "richTextButton" -> rich(raw.obj("button")?.obj("text"), inherited + RichMark.UNDERLINE)
                "richTextDiff", "richTextReference", "richTextDateTime", "richTextMention", "richTextHashtag",
                "richTextCashtag", "richTextBankCardNumber", "richTextBotCommand", "richTextMentionName",
                "richTextEmailAddress", "richTextPhoneNumber" -> child()
                "richTexts" -> concatStyled(raw.array("texts").mapNotNull { it as? JsonObject }.map { rich(it, inherited) })
                "richTextAnchor" -> StyledText.EMPTY
                else -> raw.string("text").takeIf(String::isNotBlank)?.let {
                    StyledText(listOf(StyledSpan(it, inherited)))
                } ?: StyledText.EMPTY
            }
        }

        private fun caption(raw: JsonObject?): StyledText {
            raw ?: return StyledText.EMPTY
            val text = rich(raw.obj("text"))
            val credit = rich(raw.obj("credit"))
            return if (credit.isBlank()) text else concatStyled(
                listOf(text, StyledText.plain(if (text.isBlank()) "" else "\n"), credit),
            )
        }

        private fun plainBlocks(items: JsonArray, depth: Int): String =
            blocks(items, "plain", depth).joinToString("\n") { visibleText(it) }.trim()

        private fun visibleText(block: Block): String = when (block) {
            is Block.Heading -> block.content.plainText()
            is Block.Paragraph -> block.content.plainText()
            is Block.Quote -> block.content.plainText()
            is Block.Code -> block.content.plainText()
            is Block.Bullets -> block.items.joinToString("\n")
            is Block.RichList -> block.items.joinToString("\n") { item -> item.blocks.joinToString(" ") { visibleText(it) } }
            is Block.Table -> block.rows.joinToString("\n") { it.joinToString(" | ") }
            is Block.RichTable -> block.rows.joinToString("\n") { row -> row.joinToString(" | ") { it.content.plainText() } }
            is Block.Details -> block.children.joinToString(" ") { visibleText(it) }
            is Block.RichDetails -> block.children.joinToString(" ") { visibleText(it) }
            is Block.Math -> block.expression
            is Block.Media -> block.info.caption.plainText().ifBlank { block.info.title }
            is Block.Gallery -> block.children.joinToString(" ") { visibleText(it) }
            is Block.Buttons, is Block.Divider, is Block.Unsupported -> ""
        }

        private fun animationInfo(raw: JsonObject): MediaInfo {
            val media = raw.obj("animation")
            val file = media?.obj("animation")
            return MediaInfo(
                MediaKind.ANIMATION,
                title = media?.string("file_name").orEmpty(),
                caption = caption(raw.obj("caption")),
                fileId = file?.number("id")?.toInt(),
                fileName = media?.string("file_name").orEmpty(),
                mimeType = media?.string("mime_type").orEmpty(),
                size = file?.number("size"),
                durationSeconds = media?.number("duration")?.toInt(),
                width = media?.number("width")?.toInt(),
                height = media?.number("height")?.toInt(),
            )
        }

        private fun audioInfo(raw: JsonObject): MediaInfo {
            val media = raw.obj("audio")
            val file = media?.obj("audio")
            return MediaInfo(
                MediaKind.AUDIO,
                title = listOf(media?.string("title").orEmpty(), media?.string("performer").orEmpty())
                    .filter(String::isNotBlank).joinToString(" — "),
                caption = caption(raw.obj("caption")),
                fileId = file?.number("id")?.toInt(),
                fileName = media?.string("file_name").orEmpty(),
                mimeType = media?.string("mime_type").orEmpty(),
                size = file?.number("size"),
                durationSeconds = media?.number("duration")?.toInt(),
            )
        }

        private fun documentInfo(raw: JsonObject): MediaInfo {
            val media = raw.obj("document")
            val file = media?.obj("document")
            return MediaInfo(
                MediaKind.DOCUMENT,
                title = media?.string("file_name").orEmpty(),
                caption = caption(raw.obj("caption")),
                fileId = file?.number("id")?.toInt(),
                fileName = media?.string("file_name").orEmpty(),
                mimeType = media?.string("mime_type").orEmpty(),
                size = file?.number("size"),
            )
        }

        private fun photoInfo(raw: JsonObject): MediaInfo {
            val media = raw.obj("photo")
            val best = media?.array("sizes")?.mapNotNull { it as? JsonObject }
                ?.maxByOrNull { (it.number("width") ?: 0L) * (it.number("height") ?: 0L) }
            val file = best?.obj("photo")
            return MediaInfo(
                MediaKind.PHOTO,
                caption = caption(raw.obj("caption")),
                fileId = file?.number("id")?.toInt(),
                size = file?.number("size"),
                width = best?.number("width")?.toInt(),
                height = best?.number("height")?.toInt(),
                url = safeBotUrl(raw.string("url")),
            )
        }

        private fun videoInfo(raw: JsonObject): MediaInfo {
            val media = raw.obj("video")
            val file = media?.obj("video")
            return MediaInfo(
                MediaKind.VIDEO,
                title = media?.string("file_name").orEmpty(),
                caption = caption(raw.obj("caption")),
                fileId = file?.number("id")?.toInt(),
                fileName = media?.string("file_name").orEmpty(),
                mimeType = media?.string("mime_type").orEmpty(),
                size = file?.number("size"),
                durationSeconds = media?.number("duration")?.toInt(),
                width = media?.number("width")?.toInt(),
                height = media?.number("height")?.toInt(),
            )
        }

        private fun voiceInfo(raw: JsonObject): MediaInfo {
            val media = raw.obj("voice_note")
            val file = media?.obj("voice")
            return MediaInfo(
                MediaKind.VOICE_NOTE,
                title = "Voice",
                caption = caption(raw.obj("caption")),
                fileId = file?.number("id")?.toInt(),
                mimeType = media?.string("mime_type").orEmpty(),
                size = file?.number("size"),
                durationSeconds = media?.number("duration")?.toInt(),
            )
        }
    }
}

/** Bounded per-view reducer. Revision and tombstone state never escape into another chat/account. */
internal class BotMessageReducer(val key: ChatKey, val chatId: Long) {
    private val raw = linkedMapOf<Long, JsonObject>()
    private val rendered = linkedMapOf<Long, BotMessage>()
    private val tombstones = linkedSetOf<Long>()
    private var revision = 0L
    var keyboard: BotMessage? = null
        private set
    var keyboardOneTime = false
        private set
    var keyboardVersion = 0L
        private set

    fun timeline() = MessageTimeline(key, raw.keys.mapNotNull(rendered::get))

    fun add(message: JsonObject) {
        if (message.number("chat_id") != chatId) return
        val id = message.number("id") ?: return
        if (id == 0L || id in tombstones || raw[id] == message) return
        val model = BotMessageAdapter.message(message, key, ++revision) ?: return
        raw[id] = message
        rendered[id] = model
        while (raw.size > ContentLimits.MAX_MESSAGES) {
            val oldest = raw.keys.first()
            raw.remove(oldest)
            rendered.remove(oldest)
        }
    }

    fun history(messages: List<JsonObject>) {
        val already = raw.toMap()
        val alreadyRendered = rendered.toMap()
        raw.clear()
        rendered.clear()
        messages.forEach { message ->
            val id = message.number("id") ?: return@forEach
            if (id in already) {
                raw[id] = already.getValue(id)
                rendered[id] = alreadyRendered.getValue(id)
            } else {
                add(message)
            }
        }
        already.forEach { (id, value) ->
            if (id !in raw) {
                raw[id] = value
                rendered[id] = alreadyRendered.getValue(id)
            }
        }
        while (raw.size > ContentLimits.MAX_MESSAGES) {
            val id = raw.keys.first()
            raw.remove(id)
            rendered.remove(id)
        }
    }

    fun setKeyboard(message: JsonObject?) {
        keyboardVersion++
        keyboard = message?.takeIf { it.number("chat_id") == chatId }
            ?.let { BotMessageAdapter.keyboard(it, key, ++revision) }
        keyboardOneTime = keyboard != null && message?.obj("reply_markup")?.flag("one_time") == true
    }

    fun resolve(ticket: ActionTicket): BotButton? = if (ticket.buttonId.startsWith("reply/")) {
        keyboard?.let { MessageTimeline(key, listOf(it)).resolve(ticket) }
    } else {
        timeline().resolve(ticket)
    }

    private fun forget(id: Long) {
        raw.remove(id)
        rendered.remove(id)
        tombstones.add(id)
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
                    val entries = raw.toList()
                    val finalId = message.number("id")
                    if (oldId != finalId) forget(oldId)
                    add(message)
                    if (finalId != null && entries.any { it.first == oldId }) {
                        val order = entries.map { if (it.first == oldId) finalId else it.first }.distinct()
                        val copy = raw.toMap()
                        raw.clear()
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
