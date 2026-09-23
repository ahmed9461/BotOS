package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Protocol fixtures only: never load native libraries or send requests to a Telegram server. */
class BotConversationsTest {
    private class Rpc : TdRpc {
        val calls = CopyOnWriteArrayList<JsonObject>()
        val observers = CopyOnWriteArrayList<(JsonObject) -> Unit>()
        var fullReply: (suspend () -> JsonObject)? = null
        var richHistory: JsonObject? = null
        var bot = true
        var owner = 42L
        var pendingPeriod = 0L
        var historyGate: CompletableDeferred<Unit>? = null
        var sending: (suspend () -> JsonObject)? = null
        override suspend fun request(command: JsonObject, timeoutMillis: Long): JsonObject {
            calls += command
            return when (command.type()) {
                "getMe" -> TdJson.command("user") { put("id", owner); put("first_name", "Fixture") }
                "searchPublicChat" -> TdJson.command("chat") {
                    put("id", if (command.string("username") == "beta_bot") 200 else 100)
                    put("type", TdJson.command("chatTypePrivate") { put("user_id", 77) })
                }
                "getUser" -> TdJson.command("user") { put("id", 77); put("type", TdJson.command(if (bot) "userTypeBot" else "userTypeRegular")) }
                "getChatHistory" -> {
                    historyGate?.await()
                    TdJson.command("messages") { put("messages", buildJsonArray { add(richHistory ?: wireMessage(chatId = command.number("chat_id")!!, markup = wireKeyboard(true))) }) }
                }
                "getFullRichMessage" -> fullReply?.invoke() ?: throw TdFailure(FailureKind.REMOTE)
                "getOption" -> TdJson.command("optionValueInteger") { put("value", pendingPeriod) }
                "sendMessage" -> sending?.invoke() ?: wireMessage(-1, command.number("chat_id")!!, outgoing = true, sending = "messageSendingStatePending")
                "sendBotStartMessage" -> wireMessage(-2, command.number("chat_id")!!, outgoing = true, sending = "messageSendingStatePending")
                "getCallbackQueryAnswer" -> TdJson.command("callbackQueryAnswer") { put("text", "تم"); put("url", "") }
                else -> TdJson.command("ok")
            }
        }
        override fun observeUpdates(observer: (JsonObject) -> Unit): AutoCloseable {
            observers += observer; return AutoCloseable { observers -= observer }
        }
        override suspend fun awaitClosed(timeoutMillis: Long) = Unit
        fun emit(value: JsonObject) { observers.forEach { it(value) } }
    }
    private class Fixture : AutoCloseable {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val rpc = Rpc()
        val step = MutableStateFlow(AuthStep.PARAMETERS)
        val session = object : AccountSession {
            override val step = this@Fixture.step
            override val rpc = this@Fixture.rpc
            override suspend fun initialize(credentials: TelegramAppCredentials) { step.value = AuthStep.READY }
            override suspend fun submit(expectedStep: AuthStep, value: String) = Unit
            override suspend fun close() { step.value = AuthStep.CLOSED }
            override suspend fun logOut() { step.value = AuthStep.CLOSED }
        }
        val prefs = object : ConnectionPreferences {
            var allowed = false
            override suspend fun mayRestore() = allowed
            override suspend fun setMayRestore(value: Boolean) { allowed = value }
        }
        val account = AccountCoordinator(TelegramAppCredentials(1, "0".repeat(32)), AccountSessionFactory { session }, prefs, scope)
        val live = BotConversations(account, scope)
        suspend fun connect() { account.connect(true); withTimeout(5_000) { account.ready.first { it != null } } }
        suspend fun open(name: String = "alpha_bot") { live.select(name); waitStatus(ConversationStatus.READY) }
        suspend fun waitStatus(status: ConversationStatus) = withTimeout(5_000) { live.state.first { it.status == status } }
        override fun close() { job.cancel() }
    }
    @Test fun selectingBeforeLoginDoesNotReachTelegram() = runBlocking<Unit> {
        Fixture().use { f ->
            f.live.select("alpha_bot"); delay(20)
            assertEquals(ConversationStatus.SIGN_IN, f.live.state.value.status)
            assertTrue(f.rpc.calls.isEmpty())
        }
    }
    @Test fun usernameSuffixDoesNotSubstituteForBotIdentity() = runBlocking<Unit> {
        Fixture().use { f ->
            f.rpc.bot = false; f.connect(); f.live.select("alpha_bot"); f.waitStatus(ConversationStatus.NOT_A_BOT)
            assertFalse(f.rpc.calls.any { it.type() in listOf("sendMessage", "sendBotStartMessage", "openChat") })
        }
    }
    @Test fun openingTabsNeverStartsTheBotButExplicitStartDoes() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); f.open(); assertFalse(f.rpc.calls.any { it.type() == "sendBotStartMessage" })
            assertTrue(f.live.start()); assertEquals(1, f.rpc.calls.count { it.type() == "sendBotStartMessage" })
            f.open("beta_bot"); assertEquals(1, f.rpc.calls.count { it.type() == "sendBotStartMessage" })
        }
    }
    @Test fun binaryCallbackUsesExactWireDataAndOldTabTicketCannotSend() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); f.open()
            val m = f.live.state.value.timeline!!.messages.single()
            val ticket = ActionTicket(m.chat, m.id, m.revision, "inline/0/0")
            assertTrue(f.live.activate(ticket))
            val call = f.rpc.calls.single { it.type() == "getCallbackQueryAnswer" }
            assertEquals("AP+AAQ==", call.obj("payload")?.string("data"))
            f.open("beta_bot"); assertFalse(f.live.activate(ticket))
            assertEquals(1, f.rpc.calls.count { it.type() == "getCallbackQueryAnswer" })
        }
    }
    @Test fun timeoutDoesNotRepeatASideEffectAndIsNotReportedAsSent() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); f.open()
            f.rpc.sending = { withTimeout(1) { delay(50); wireMessage(-1) } }
            assertFalse(f.live.send("hello"))
            assertEquals(ConversationIssue.UNCERTAIN, f.live.state.value.issue)
            assertEquals(1, f.rpc.calls.count { it.type() == "sendMessage" })
        }
    }
    @Test fun sendSuccessRacingItsResponseLeavesOneFinalMessage() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); f.open()
            f.rpc.sending = {
                f.rpc.emit(TdJson.command("updateMessageSendSucceeded") { put("old_message_id", -1); put("message", wireMessage(44, outgoing = true)) })
                delay(30)
                wireMessage(-1, outgoing = true, sending = "messageSendingStatePending")
            }
            assertTrue(f.live.send("hi"))
            withTimeout(5_000) { f.live.state.first { state -> state.timeline?.messages?.any { it.id == 44L } == true } }
            assertFalse(f.live.state.value.timeline!!.messages.any { it.id == -1L })
        }
    }
    @Test fun logoutClearsContentsAndNewAccountRejectsOldActions() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); f.open()
            val before = f.live.state.value.timeline!!.messages.single()
            val ticket = ActionTicket(before.chat, before.id, before.revision, "inline/0/0")
            f.account.logOut(); f.waitStatus(ConversationStatus.SIGN_IN)
            assertNull(f.live.state.value.timeline)
            f.rpc.owner = 43; f.connect(); f.waitStatus(ConversationStatus.READY)
            assertNotEquals(before.chat, f.live.state.value.timeline!!.chat)
            assertFalse(f.live.activate(ticket))
        }
    }
    @Test fun switchingTabsClearsOldContentsBeforeHistoryFinishes() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); f.open(); val gate = CompletableDeferred<Unit>(); f.rpc.historyGate = gate
            f.live.select("beta_bot"); assertNull(f.live.state.value.timeline)
            gate.complete(Unit); f.waitStatus(ConversationStatus.READY)
            assertTrue(f.live.state.value.timeline!!.chat.chat.startsWith("200:"))
        }
    }
    @Test fun editArrivingDuringInitialHistoryWinsBeforeButtonsBecomeUsable() = runBlocking<Unit> {
        Fixture().use { f ->
            val gate = CompletableDeferred<Unit>(); f.rpc.historyGate = gate
            f.connect(); f.live.select("alpha_bot")
            withTimeout(5_000) { while (f.rpc.calls.none { it.type() == "getChatHistory" }) delay(1) }
            f.rpc.emit(TdJson.command("updateMessageEdited") { put("chat_id", 100); put("message_id", 10); put("reply_markup", JsonNull) })
            gate.complete(Unit); f.waitStatus(ConversationStatus.READY)
            assertFalse(f.live.state.value.timeline!!.messages.single().blocks.any { it is Block.Buttons })
        }
    }
    @Test fun updateOverflowFailsClosedInsteadOfSilentlyDroppingButtonEdits() = runBlocking<Unit> {
        Fixture().use { f ->
            val gate = CompletableDeferred<Unit>(); f.rpc.historyGate = gate
            f.connect(); f.live.select("alpha_bot")
            withTimeout(5_000) { while (f.rpc.calls.none { it.type() == "getChatHistory" }) delay(1) }
            repeat(300) { n -> f.rpc.emit(TdJson.command("updateNewMessage") { put("message", wireMessage(1000L + n)) }) }
            gate.complete(Unit); f.waitStatus(ConversationStatus.FAILED)
            assertEquals(ConversationIssue.OVERFLOW, f.live.state.value.issue)
            assertFalse(f.live.send("must not send"))
            assertFalse(f.rpc.calls.any { it.type() == "sendMessage" })
        }
    }
    @Test fun partialRichMessagesAreFetchedOnceAndRemainBoundToTheSelectedChat() = runBlocking<Unit> {
        Fixture().use { f ->
            val full = TdJson.command("richMessage") {
                put("is_full", true)
                put("is_rtl", true)
                put("blocks", buildJsonArray {
                    add(TdJson.command("pageBlockParagraph") {
                        put("text", TdJson.command("richTextPlain") {
                            put("text", "كامل")
                        })
                    })
                })
            }
            val partial = JsonObject(full.toMutableMap().apply { put("is_full", JsonPrimitive(false)) })
            f.rpc.richHistory = TdJson.command("message") {
                put("id", 50); put("chat_id", 100); put("content", TdJson.command("messageRichMessage") { put("message", partial) })
            }
            f.rpc.fullReply = { full }
            f.connect(); f.open()
            withTimeout(5_000) { f.live.state.first { it.timeline?.messages?.singleOrNull()?.isFull == true } }
            assertEquals(1, f.rpc.calls.count { it.type() == "getFullRichMessage" })
            assertFalse(f.rpc.calls.any { it.type() == "sendMessage" || it.type() == "sendBotStartMessage" })
            f.rpc.richHistory = null
            f.open("beta_bot")
            assertEquals("مرحبا 🌚", (f.live.state.value.timeline!!.messages.single().blocks.first() as Block.Paragraph).text)
        }
    }

    @Test fun pendingTextAndRichUpdatesAreIsolatedAcrossTabSwitchAndDisconnect() = runBlocking<Unit> {
        Fixture().use { f ->
            f.rpc.pendingPeriod = 3
            f.connect(); f.open("alpha_bot")
            fun pending(chatId: Long, id: Long, text: String, rich: Boolean): JsonObject =
                TdJson.command("updatePendingMessage") {
                    put("chat_id", chatId); put("draft_id", id); put("can_stop", true)
                    put("content", if (rich) TdJson.command("messageRichMessage") {
                        put("message", TdJson.command("richMessage") {
                            put("is_full", true); put("blocks", buildJsonArray {
                                add(TdJson.command("pageBlockParagraph") {
                                    put("text", TdJson.command("richTextPlain") { put("text", text) })
                                })
                            })
                        })
                    } else TdJson.command("messageText") {
                        put("text", TdJson.command("formattedText") { put("text", text) })
                    })
                }
            f.rpc.emit(pending(100, 7, "نص", false))
            withTimeout(5_000) { f.live.state.first { it.pending?.draftId == 7L } }
            f.rpc.emit(pending(100, 7, "غني", true))
            withTimeout(5_000) { f.live.state.first { state ->
                (state.pending?.content?.blocks?.singleOrNull() as? Block.Paragraph)?.text == "غني"
            } }
            val previous = f.live.state.value.pending!!.content.chat
            f.open("beta_bot")
            assertNull(f.live.state.value.pending)
            f.rpc.emit(pending(100, 8, "قديم", true))
            delay(50)
            assertNull(f.live.state.value.pending)
            f.rpc.emit(pending(200, 9, "جديد", false))
            withTimeout(5_000) { f.live.state.first { it.pending?.draftId == 9L } }
            assertNotEquals(previous, f.live.state.value.pending!!.content.chat)
            f.account.logOut(); f.waitStatus(ConversationStatus.SIGN_IN)
            assertNull(f.live.state.value.pending)
            assertFalse(f.rpc.calls.any { it.type() == "sendMessage" || it.type() == "getCallbackQueryAnswer" })
        }
    }
    @Test fun capturedAttachmentTargetCannotSendAfterBotSwitch() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); f.open("alpha_bot")
            val target = f.live.captureAttachmentTarget()!!
            f.open("beta_bot")
            val result = f.live.sendAttachment(target,
                PreparedAttachment("/private/file.jpg", AttachmentKind.PHOTO, 100, 10, 10), "", 91)
            assertEquals(AttachmentSendResult.Rejected, result)
            assertFalse(f.rpc.calls.any { it.type() == "sendMessage" && it.obj("options")?.number("sending_id") == 91L })
        }
    }

    @Test fun attachmentSendUsesExactSendingIdAndCompletionSurvivesBotSwitch() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); f.open("alpha_bot")
            val target = f.live.captureAttachmentTarget()!!
            f.rpc.sending = {
                val raw = wireMessage(-50, 100, outgoing = true).toMutableMap()
                raw["sending_state"] = TdJson.command("messageSendingStatePending") { put("sending_id", 777) }
                JsonObject(raw)
            }
            val observed = async { withTimeout(5_000) { f.live.uploadEvents.first { it is UploadEvent.Succeeded } } }
            val result = f.live.sendAttachment(target,
                PreparedAttachment("/private/file.jpg", AttachmentKind.PHOTO, 100, 10, 10), "وصف", 777)
            assertEquals(AttachmentSendResult.Pending(777, -50), result)
            val call = f.rpc.calls.last { it.type() == "sendMessage" }
            assertEquals(777L, call.obj("options")?.number("sending_id"))
            assertEquals("وصف", call.obj("input_message_content")?.obj("caption")?.string("text"))
            f.open("beta_bot")
            f.rpc.emit(TdJson.command("updateMessageSendSucceeded") {
                put("old_message_id", -50)
                put("message", wireMessage(501, 100, outgoing = true))
            })
            assertEquals(UploadEvent.Succeeded(target.chat.account, 100, 777, -50, 501), observed.await())
        }
    }

    @Test fun attachmentTimeoutIsUncertainAndNeverRetried() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); f.open()
            val target = f.live.captureAttachmentTarget()!!
            f.rpc.sending = { withTimeout(1) { delay(50); wireMessage(-60) } }
            val result = f.live.sendAttachment(target,
                PreparedAttachment("/private/file.m4a", AttachmentKind.VOICE, 100, duration = 2), "", 778)
            assertEquals(AttachmentSendResult.Uncertain(778), result)
            assertEquals(1, f.rpc.calls.count { it.type() == "sendMessage" && it.obj("options")?.number("sending_id") == 778L })
        }
    }

    @Test fun recoveryProbeRejectsWrongAccountGenerationBeforeNetworkAndNeverResends() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); f.open()
            val target = f.live.captureAttachmentTarget()!!
            val wrongGeneration = target.chat.account.substringBefore(':') + ":" + (target.accountGeneration + 1)
            val before = f.rpc.calls.size
            val result = f.live.inspectAttachment(wrongGeneration, 100, 10, 222)
            assertNull(result)
            assertEquals(before, f.rpc.calls.size)
            assertFalse(f.rpc.calls.any { it.type() == "sendMessage" && it.obj("options")?.number("sending_id") == 222L })
        }
    }

    @Test fun terminalUpdateBeforeSendResponseIsReconciledWithoutRetry() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); f.open()
            val target = f.live.captureAttachmentTarget()!!
            f.rpc.sending = {
                val pending = wireMessage(-70, 100, outgoing = true).toMutableMap()
                pending["sending_state"] = TdJson.command("messageSendingStatePending") { put("sending_id", 779) }
                f.rpc.emit(TdJson.command("updateMessageSendSucceeded") {
                    put("old_message_id", -70)
                    put("message", wireMessage(701, 100, outgoing = true))
                })
                JsonObject(pending)
            }
            val terminal = async { withTimeout(5_000) { f.live.uploadEvents.first { it is UploadEvent.Succeeded && it.sendingId == 779 } } }
            val result = f.live.sendAttachment(target,
                PreparedAttachment("/private/file.jpg", AttachmentKind.PHOTO, 100, 10, 10), "", 779)
            assertEquals(AttachmentSendResult.Pending(779, -70), result)
            assertEquals(UploadEvent.Succeeded(target.chat.account, 100, 779, -70, 701), terminal.await())
            assertEquals(1, f.rpc.calls.count { it.type() == "sendMessage" && it.obj("options")?.number("sending_id") == 779L })
        }
    }

    @Test fun equalTemporaryIdsInDifferentChatsDoNotCrossComplete() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); f.open()
            val target = f.live.captureAttachmentTarget()!!
            f.rpc.sending = {
                val pending = wireMessage(-80, 100, outgoing = true).toMutableMap()
                pending["sending_state"] = TdJson.command("messageSendingStatePending") { put("sending_id", 780) }
                f.rpc.emit(TdJson.command("updateMessageSendSucceeded") {
                    put("old_message_id", -80)
                    put("message", wireMessage(880, 200, outgoing = true))
                })
                JsonObject(pending)
            }
            val observed = CopyOnWriteArrayList<UploadEvent>()
            val collect = f.scope.launch(start = CoroutineStart.UNDISPATCHED) {
                f.live.uploadEvents.collect { observed += it }
            }
            try {
                assertEquals(AttachmentSendResult.Pending(780, -80), f.live.sendAttachment(target,
                    PreparedAttachment("/private/file.jpg", AttachmentKind.PHOTO, 100, 10, 10), "", 780))
                f.rpc.emit(TdJson.command("updateMessageSendSucceeded") {
                    put("old_message_id", -80)
                    put("message", wireMessage(881, 100, outgoing = true))
                })
                withTimeout(5_000) { while (observed.none { it is UploadEvent.Succeeded && it.messageId == 881L }) delay(1) }
                assertFalse(observed.any { it is UploadEvent.Succeeded && it.messageId == 880L })
            } finally { collect.cancel() }
        }
    }

}
