package com.ahmed9461.botos.telegram.runtime

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class PendingStopIsolationTest {
    @Test fun sameDraftIdInAnotherChatCannotBeStoppedByAnOldUiAction() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = CopyOnWriteArrayList<JsonObject>()
        val observers = CopyOnWriteArrayList<(JsonObject) -> Unit>()
        val fakeRpc = object : TdRpc {
            override suspend fun request(command: JsonObject, timeoutMillis: Long): JsonObject {
                calls += command
                return when (command.type()) {
                    "getMe" -> TdJson.command("user") { put("id", 42) }
                    "searchPublicChat" -> TdJson.command("chat") {
                        put("id", if (command.string("username") == "beta_bot") 200 else 100)
                        put("type", TdJson.command("chatTypePrivate") { put("user_id", 9) })
                    }
                    "getUser" -> TdJson.command("user") { put("id", 9); put("type", TdJson.command("userTypeBot")) }
                    "getOption" -> TdJson.command("optionValueInteger") { put("value", 30) }
                    "getChatHistory" -> TdJson.command("messages") { put("messages", JsonArray(emptyList())) }
                    else -> TdJson.command("ok")
                }
            }
            override fun observeUpdates(observer: (JsonObject) -> Unit): AutoCloseable {
                observers += observer; return AutoCloseable { observers -= observer }
            }
            override suspend fun awaitClosed(timeoutMillis: Long) = Unit
        }
        val stepsFlow = MutableStateFlow(AuthStep.PARAMETERS)
        val account = AccountCoordinator(TelegramAppCredentials(1, "0".repeat(32)), AccountSessionFactory {
            object : AccountSession {
                override val step = stepsFlow
                override val rpc = fakeRpc
                override suspend fun initialize(credentials: TelegramAppCredentials) { stepsFlow.value = AuthStep.READY }
                override suspend fun submit(expectedStep: AuthStep, value: String) = Unit
                override suspend fun close() { stepsFlow.value = AuthStep.CLOSED }
                override suspend fun logOut() { stepsFlow.value = AuthStep.CLOSED }
            }
        }, object : ConnectionPreferences {
            override suspend fun mayRestore() = false
            override suspend fun setMayRestore(value: Boolean) = Unit
        }, scope)
        val live = BotConversations(account, scope)
        try {
            account.connect(true)
            withTimeout(5_000) { account.ready.first { it != null } }
            suspend fun open(username: String, id: Long) {
                live.select(username)
                withTimeout(5_000) { live.state.first { it.status == ConversationStatus.READY && it.username == username } }
                val update = TdJson.command("updatePendingMessage") {
                    put("chat_id", id); put("draft_id", 77); put("forum_topic_id", 0)
                    put("can_stop", true); put("keep_on_stop", true)
                    put("content", TdJson.command("messageText") {
                        put("text", TdJson.command("formattedText") { put("text", "يكتب") })
                    })
                }
                observers.forEach { it(update) }
                withTimeout(5_000) { live.state.first { it.pending?.draftId == 77L } }
            }
            open("alpha_bot", 100)
            val oldChat = live.state.value.timeline!!.chat
            open("beta_bot", 200)
            assertFalse(live.stopPending(77, oldChat))
            assertTrue(calls.none { it.type() == "stopPendingMessage" })
            assertTrue(live.stopPending(77, live.state.value.timeline!!.chat))
            assertEquals(200L, calls.single { it.type() == "stopPendingMessage" }.number("chat_id"))
            assertTrue(live.state.value.pending!!.stopped)
            assertFalse(live.state.value.pending!!.canStop)
        } finally { scope.cancel() }
    }
}
