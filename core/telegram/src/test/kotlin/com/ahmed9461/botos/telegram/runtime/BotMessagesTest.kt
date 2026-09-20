package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

internal fun wireMessage(
    id: Long = 10,
    chatId: Long = 100,
    text: String = "مرحبا 🌚",
    outgoing: Boolean = false,
    sending: String? = null,
    markup: JsonObject? = null,
): JsonObject = TdJson.command("message") {
    put("id", id)
    put("chat_id", chatId)
    put("is_outgoing", outgoing)
    put("content", TdJson.command("messageText") {
        put("text", TdJson.command("formattedText") { put("text", text) })
    })
    sending?.let { put("sending_state", TdJson.command(it)) }
    markup?.let { put("reply_markup", it) }
}

internal fun wireKeyboard(
    inline: Boolean,
    type: String = if (inline) "inlineKeyboardButtonTypeCallback" else "keyboardButtonTypeText",
    data: String = "AP+AAQ==",
    oneTime: Boolean = false,
): JsonObject = TdJson.command(if (inline) "replyMarkupInlineKeyboard" else "replyMarkupShowKeyboard") {
    put("one_time", oneTime)
    put("rows", buildJsonArray {
        add(buildJsonArray {
            add(buildJsonObject {
                put("text", "قسم خاص")
                put("type", TdJson.command(type) {
                    put("data", data)
                    put("url", data)
                })
            })
        })
    })
}

private fun richPlain(text: String) = TdJson.command("richTextPlain") { put("text", text) }
private fun richWrap(type: String, child: JsonObject) = TdJson.command(type) { put("text", child) }
private fun pageParagraph(text: JsonObject) = TdJson.command("pageBlockParagraph") { put("text", text) }

private fun wireRichMessage(blocks: JsonArray, rtl: Boolean = true, id: Long = 50L): JsonObject =
    TdJson.command("message") {
        put("id", id)
        put("chat_id", 100)
        put("date", 1_789_000_000)
        put("is_outgoing", false)
        put("content", TdJson.command("messageRichMessage") {
            put("message", TdJson.command("richMessage") {
                put("blocks", blocks)
                put("is_rtl", rtl)
                put("is_full", true)
            })
        })
    }

class BotMessagesTest {
    private val key = ChatKey("42:1", "100:2")

    @Test fun callbackBinaryDataAndRowsAreNotGuessedFromLabels() {
        val m = BotMessageAdapter.message(wireMessage(markup = wireKeyboard(true)), key, 1)!!
        val button = (m.blocks.last() as Block.Buttons).buttons.single()
        assertEquals(ActionPayload.Callback("AP+AAQ=="), button.payload)
        assertEquals("قسم خاص", button.label)
    }

    @Test fun specialButtonsAreExplicitlyDisabled() {
        val m = BotMessageAdapter.message(
            wireMessage(markup = wireKeyboard(true, "inlineKeyboardButtonTypeBuy")),
            key,
            1,
        )!!
        val b = (m.blocks.last() as Block.Buttons).buttons.single()
        assertFalse(b.enabled)
        assertEquals(ActionPayload.Unsupported, b.payload)
    }

    @Test fun richMessagePreservesFormattingDirectionTimestampAndButtonPayload() {
        val blocks = buildJsonArray {
            add(TdJson.command("pageBlockSectionHeading") {
                put("text", richWrap("richTextBold", richPlain("🤖 PixelPilot")))
                put("size", 2)
            })
            add(pageParagraph(TdJson.command("richTexts") {
                put("texts", buildJsonArray {
                    add(richPlain("مساعدك "))
                    add(richWrap("richTextItalic", richPlain("الشخصي")))
                    add(richPlain(" متعدد الوسائط."))
                })
            }))
            add(TdJson.command("pageBlockButtonRow") {
                put("align", TdJson.command("pageBlockHorizontalAlignmentCenter"))
                put("buttons", buildJsonArray {
                    add(TdJson.command("inlineButton") {
                        put("text", richPlain("⚙️ إعدادات المساعد"))
                        put("style", TdJson.command("buttonStylePrimary"))
                        put("type", TdJson.command("inlineKeyboardButtonTypeCallback") { put("data", "AQID") })
                    })
                })
            })
        }
        val message = BotMessageAdapter.message(wireRichMessage(blocks), key, 9)!!
        assertEquals(true, message.forceRtl)
        assertEquals(1_789_000_000L, message.date)
        val heading = message.blocks[0] as Block.Heading
        assertEquals("🤖 PixelPilot", heading.text)
        assertTrue(heading.content.spans.single().marks.contains(RichMark.BOLD))
        val paragraph = message.blocks[1] as Block.Paragraph
        assertTrue(paragraph.content.spans.any { RichMark.ITALIC in it.marks && it.text == "الشخصي" })
        val button = (message.blocks[2] as Block.Buttons).buttons.single()
        assertEquals(ButtonVisualStyle.PRIMARY, button.style)
        assertEquals(ActionPayload.Callback("AQID"), button.payload)
    }

    @Test fun richMessageConvertsListsTablesDetailsQuotesAndMathWithoutGenericFallback() {
        val blocks = buildJsonArray {
            add(TdJson.command("pageBlockList") {
                put("items", buildJsonArray {
                    add(TdJson.command("pageBlockListItem") {
                        put("label", "1")
                        put("has_checkbox", true)
                        put("is_checked", true)
                        put("blocks", buildJsonArray { add(pageParagraph(richPlain("عنصر مكتمل"))) })
                    })
                })
            })
            add(TdJson.command("pageBlockTable") {
                put("caption", richPlain("النتيجة"))
                put("is_compact", true)
                put("cells", buildJsonArray {
                    add(buildJsonArray {
                        add(TdJson.command("pageBlockTableCell") {
                            put("text", richWrap("richTextBold", richPlain("الحالة")))
                            put("is_header", true)
                            put("colspan", 1)
                            put("rowspan", 1)
                        })
                        add(TdJson.command("pageBlockTableCell") {
                            put("text", richPlain("جاهز"))
                            put("is_header", false)
                            put("colspan", 1)
                            put("rowspan", 1)
                        })
                    })
                })
            })
            add(TdJson.command("pageBlockDetails") {
                put("header", richPlain("ما الذي أستطيع إرساله؟"))
                put("is_open", false)
                put("blocks", buildJsonArray { add(pageParagraph(richPlain("نصوص وصور وملفات"))) })
            })
            add(TdJson.command("pageBlockExpandableBlockQuote") {
                put("text", richPlain("اقتباس قابل للتوسيع"))
                put("credit", richPlain("المصدر"))
            })
            add(TdJson.command("pageBlockMathematicalExpression") { put("expression", "E = mc²") })
            add(TdJson.command("pageBlockDivider"))
        }
        val message = BotMessageAdapter.message(wireRichMessage(blocks), key, 1)!!
        assertTrue(message.blocks.any { it is Block.RichList })
        assertTrue(message.blocks.any { it is Block.RichTable })
        assertTrue(message.blocks.any { it is Block.RichDetails })
        assertTrue(message.blocks.any { it is Block.Quote && it.expandable })
        assertTrue(message.blocks.any { it is Block.Math })
        assertTrue(message.blocks.any { it is Block.Divider })
        assertFalse(message.blocks.any { it is Block.Unsupported })
    }

    @Test fun richMediaFamiliesBecomeTypedCardsInsteadOfUnavailableContent() {
        fun file(id: Int, size: Int = 1234) = TdJson.command("file") {
            put("id", id)
            put("size", size)
        }
        val blocks = buildJsonArray {
            add(TdJson.command("pageBlockDocument") {
                put("document", TdJson.command("document") {
                    put("file_name", "guide.pdf")
                    put("mime_type", "application/pdf")
                    put("document", file(7))
                })
                put("caption", TdJson.command("pageBlockCaption") {
                    put("text", richPlain("الدليل"))
                    put("credit", richPlain(""))
                })
            })
            add(TdJson.command("pageBlockVideo") {
                put("video", TdJson.command("video") {
                    put("file_name", "clip.mp4")
                    put("mime_type", "video/mp4")
                    put("duration", 9)
                    put("width", 1280)
                    put("height", 720)
                    put("video", file(8))
                })
                put("caption", TdJson.command("pageBlockCaption") {
                    put("text", richPlain("مقطع"))
                    put("credit", richPlain(""))
                })
            })
            add(TdJson.command("pageBlockPhoto") {
                put("photo", TdJson.command("photo") {
                    put("sizes", buildJsonArray {
                        add(TdJson.command("photoSize") {
                            put("width", 640)
                            put("height", 480)
                            put("photo", file(9))
                        })
                    })
                })
                put("caption", TdJson.command("pageBlockCaption") {
                    put("text", richPlain("صورة"))
                    put("credit", richPlain(""))
                })
                put("url", "")
            })
            add(TdJson.command("pageBlockMap") {
                put("location", TdJson.command("location") {
                    put("latitude", 15.35)
                    put("longitude", 44.20)
                })
                put("caption", TdJson.command("pageBlockCaption") {
                    put("text", richPlain("الموقع"))
                    put("credit", richPlain(""))
                })
            })
        }
        val message = BotMessageAdapter.message(wireRichMessage(blocks), key, 1)!!
        val media = message.blocks.filterIsInstance<Block.Media>()
        assertEquals(4, media.size)
        assertEquals(listOf(MediaKind.DOCUMENT, MediaKind.VIDEO, MediaKind.PHOTO, MediaKind.MAP), media.map { it.info.kind })
        assertEquals("guide.pdf", media.first().info.fileName)
        assertEquals(7, media.first().info.fileId)
        assertEquals("الدليل", media.first().info.caption.plainText())
        assertFalse(message.blocks.any { it is Block.Unsupported })
    }

    @Test fun richCompositeTextHandlesCurrentNestedMarksAndSafeLinks() {
        val text = TdJson.command("richTexts") {
            put("texts", buildJsonArray {
                add(richWrap("richTextBold", richWrap("richTextUnderline", richPlain("مهم"))))
                add(TdJson.command("richTextUrl") {
                    put("text", richPlain(" رابط"))
                    put("url", "https://example.org/help")
                })
                add(TdJson.command("richTextCustomEmoji") {
                    put("custom_emoji_id", 1)
                    put("alternative_text", "✅")
                })
                add(TdJson.command("richTextMathematicalExpression") { put("expression", "x²") })
            })
        }
        val message = BotMessageAdapter.message(
            wireRichMessage(buildJsonArray { add(pageParagraph(text)) }, rtl = false),
            key,
            1,
        )!!
        val paragraph = message.blocks.single() as Block.Paragraph
        assertEquals(false, message.forceRtl)
        assertTrue(paragraph.content.spans.first().marks.containsAll(setOf(RichMark.BOLD, RichMark.UNDERLINE)))
        assertEquals("https://example.org/help", paragraph.content.spans.first { it.text.contains("رابط") }.url)
        assertTrue(paragraph.content.spans.any { RichMark.MATH in it.marks })
        assertTrue(paragraph.text.contains("✅"))
    }

    @Test fun unknownRichBlockRemainsExplicitlyUnsupported() {
        val message = BotMessageAdapter.message(
            wireRichMessage(buildJsonArray { add(TdJson.command("pageBlockFutureUnknown")) }),
            key,
            1,
        )!!
        assertTrue(message.blocks.single() is Block.Unsupported)
    }

    @Test fun updatesSupersedeLateHistoryAndInvalidateOldTickets() {
        val r = BotMessageReducer(key, 100)
        r.add(wireMessage(markup = wireKeyboard(true)))
        val m = r.timeline().messages.single()
        val t = ActionTicket(key, m.id, m.revision, "inline/0/0")
        assertNotNull(r.resolve(t))
        r.update(TdJson.command("updateMessageEdited") {
            put("chat_id", 100)
            put("message_id", 10)
            put("reply_markup", JsonNull)
        })
        r.history(listOf(wireMessage(markup = wireKeyboard(true))))
        assertNull(r.resolve(t))
        assertFalse(r.timeline().messages.single().blocks.any { it is Block.Buttons })
    }

    @Test fun finalSendUpdateCannotBeRolledBackByLatePendingResponse() {
        val r = BotMessageReducer(key, 100)
        r.update(TdJson.command("updateMessageSendSucceeded") {
            put("old_message_id", -1)
            put("message", wireMessage(22, outgoing = true))
        })
        r.add(wireMessage(-1, outgoing = true, sending = "messageSendingStatePending"))
        assertEquals(listOf(22L), r.timeline().messages.map { it.id })
        assertEquals(DeliveryState.SENT, r.timeline().messages.single().delivery)
    }

    @Test fun sendFailureMayKeepTheSameMessageId() {
        val r = BotMessageReducer(key, 100)
        r.add(wireMessage(-1, outgoing = true, sending = "messageSendingStatePending"))
        r.update(TdJson.command("updateMessageSendFailed") {
            put("old_message_id", -1)
            put("message", wireMessage(-1, outgoing = true, sending = "messageSendingStateFailed"))
        })
        assertEquals(DeliveryState.FAILED, r.timeline().messages.single().delivery)
    }

    @Test fun onlyCurrentReplyKeyboardCanExecuteAndNullUpdateHidesIt() {
        val r = BotMessageReducer(key, 100)
        r.setKeyboard(wireMessage(markup = wireKeyboard(false, oneTime = true)))
        val m = r.keyboard!!
        val t = ActionTicket(key, m.id, m.revision, "reply/0/0")
        assertTrue(r.keyboardOneTime)
        assertEquals(ActionPayload.SendText("قسم خاص"), r.resolve(t)?.payload)
        r.update(TdJson.command("updateChatReplyMarkup") {
            put("chat_id", 100)
            put("reply_markup_message", JsonNull)
        })
        assertNull(r.keyboard)
        assertNull(r.resolve(t))
    }

    @Test fun foreignChatAndDeletedHistoryCannotAppearInCurrentTimeline() {
        val r = BotMessageReducer(key, 100)
        r.add(wireMessage(chatId = 200))
        assertTrue(r.timeline().messages.isEmpty())
        r.add(wireMessage())
        r.update(TdJson.command("updateDeleteMessages") {
            put("chat_id", 100)
            put("is_permanent", true)
            put("message_ids", buildJsonArray { add(10) })
        })
        r.history(listOf(wireMessage()))
        assertTrue(r.timeline().messages.isEmpty())
    }

    @Test fun linksRejectExecutableSchemesCredentialsAndControlCharacters() {
        assertEquals("https://example.org/path", safeBotUrl("https://example.org/path"))
        listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "intent://open",
            "https://user:pass@example.org",
            "https://example.org/\n",
        ).forEach { assertNull(safeBotUrl(it)) }
    }

    @Test fun messageAndKeyboardBudgetsAreBounded() {
        val r = BotMessageReducer(key, 100)
        repeat(250) { r.add(wireMessage(it.toLong() + 1)) }
        assertEquals(ContentLimits.MAX_MESSAGES, r.timeline().messages.size)
        assertNull(r.resolve(ActionTicket(ChatKey("other", "100:2"), 250, 250, "inline/0/0")))
    }
}
