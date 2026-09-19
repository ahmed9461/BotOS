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
                put("is_full", true); put("is_rtl", true); put("blocks", buildJsonArray {
                    add(TdJson.command("pageBlockParagraph") { put("text", TdJson.command("richTextPlain") { put("text", "كامل") }) })
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
}
