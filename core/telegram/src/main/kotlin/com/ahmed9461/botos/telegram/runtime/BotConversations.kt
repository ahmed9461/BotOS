package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.*
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.*

enum class ConversationStatus { SIGN_IN, LOADING, READY, NOT_A_BOT, FAILED }
enum class ConversationIssue { CONNECTION, UNCERTAIN, STALE_ACTION, UNSUPPORTED, OVERFLOW }
data class ConversationState(
    val username: String? = null,
    val status: ConversationStatus = ConversationStatus.SIGN_IN,
    val timeline: MessageTimeline? = null,
    val keyboard: BotMessage? = null,
    val busy: Boolean = false,
    val issue: ConversationIssue? = null,
    val notice: String? = null,
    val proposedUrl: String? = null,
    val generation: Long = 0,
) {
    override fun toString() = "ConversationState(status=$status, busy=$busy, issue=$issue)"
}

/** One active bot, bounded updates, and generation checks around every asynchronous boundary. */
class BotConversations(private val account: AccountCoordinator, scope: CoroutineScope) {
    private val engineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val engineScope = CoroutineScope(scope.coroutineContext + engineDispatcher)
    private data class Selection(val username: String?, val serial: Long)
    private class Binding(val selection: Selection, val account: ReadyAccount, val chatId: Long, val botId: Long,
        val reducer: BotMessageReducer) {
        @Volatile var faulted = false
        val operation = Mutex()
    }
    private val serial = AtomicLong()
    private val selected = MutableStateFlow(Selection(null, 0))
    private val _state = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = _state.asStateFlow()
    @Volatile private var binding: Binding? = null

    init {
        engineScope.launch {
            combine(account.ready, selected) { owner, selection -> owner to selection }.collectLatest { (owner, selection) ->
                if (account.ready.value !== owner || selected.value != selection) return@collectLatest
                binding = null
                _state.update { if (account.ready.value === owner && selected.value == selection) ConversationState(selection.username, generation = selection.serial) else it }
                if (owner == null || selection.username == null) return@collectLatest
                _state.update { if (current(owner, selection)) ConversationState(selection.username, ConversationStatus.LOADING, generation = selection.serial) else it }
                var subscription: AutoCloseable? = null
                var openedChat: Long? = null
                val updates = Channel<JsonObject>(256)
                try {
                    val chat = owner.rpc.request(TdJson.command("searchPublicChat") { put("username", selection.username) })
                    if (!current(owner, selection)) return@collectLatest
                    val type = chat.obj("type")
                    val chatId = chat.number("id")
                    val botId = type?.number("user_id")
                    if (chat.type() != "chat" || type?.type() != "chatTypePrivate" || chatId == null || botId == null) {
                        _state.update { if (current(owner, selection)) ConversationState(selection.username, ConversationStatus.NOT_A_BOT, generation = selection.serial) else it }; return@collectLatest
                    }
                    val bot = owner.rpc.request(TdJson.command("getUser") { put("user_id", botId) })
                    if (!current(owner, selection)) return@collectLatest
                    if (bot.type() != "user" || bot.number("id") != botId || bot.obj("type")?.type() != "userTypeBot") {
                        _state.update { if (current(owner, selection)) ConversationState(selection.username, ConversationStatus.NOT_A_BOT, generation = selection.serial) else it }; return@collectLatest
                    }
                    val key = ChatKey("${owner.userId}:${owner.generation}", "$chatId:${selection.serial}")
                    val active = Binding(selection, owner, chatId, botId, BotMessageReducer(key, chatId))
                    binding = active
                    subscription = owner.rpc.observeUpdates { update ->
                        val updateChat = update.obj("message")?.number("chat_id") ?: update.number("chat_id")
                        if (updateChat == chatId && updates.trySend(update).isFailure) updates.close(TdFailure(FailureKind.BUSY))
                    }
                    coroutineScope {
                        owner.rpc.request(TdJson.command("openChat") { put("chat_id", chatId) })
                        openedChat = chatId
                        val keyboardVersion = active.reducer.keyboardVersion
                        val history = owner.rpc.request(TdJson.command("getChatHistory") {
                            put("chat_id", chatId); put("from_message_id", 0); put("offset", 0); put("limit", 50); put("only_local", false)
                        })
                        if (!valid(active)) return@coroutineScope
                        if (history.type() != "messages") throw TdFailure(FailureKind.PROTOCOL)
                        active.reducer.history(history.array("messages").mapNotNull { it as? JsonObject }.reversed())
                        val keyboardId = chat.number("reply_markup_message_id") ?: 0
                        if (keyboardId != 0L && active.reducer.keyboardVersion == keyboardVersion) {
                            val keyboard = owner.rpc.request(TdJson.command("getMessage") { put("chat_id", chatId); put("message_id", keyboardId) })
                            if (valid(active) && active.reducer.keyboardVersion == keyboardVersion) active.reducer.setKeyboard(keyboard)
                        }
                        // Apply queued updates after snapshots; early edits of unseen messages must not be lost.
                        val consumer = launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                for (update in updates) {
                                    if (!valid(active)) break
                                    active.reducer.update(update)
                                    publish(active)
                                }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) {
                                if (valid(active)) {
                                    active.faulted = true
                                    change(active) { it.copy(status = ConversationStatus.FAILED, issue = ConversationIssue.OVERFLOW) }
                                }
                            }
                        }
                        if (valid(active) && !active.faulted) {
                            change(active) { it.copy(status = ConversationStatus.READY, timeline = active.reducer.timeline(), keyboard = active.reducer.keyboard) }
                        }
                        consumer.join()
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    _state.update { if (current(owner, selection)) it.copy(status = ConversationStatus.FAILED, issue = ConversationIssue.CONNECTION, busy = false) else it }
                } finally {
                    subscription?.close(); updates.close()
                    if (binding?.selection == selection) binding = null
                    openedChat?.let { id ->
                        withContext(NonCancellable) {
                            try { owner.rpc.request(TdJson.command("closeChat") { put("chat_id", id) }, timeoutMillis = 2_000) }
                            catch (_: Exception) { /* View bookkeeping only, never a send or logout retry. */ }
                        }
                    }
                }
            }
        }
    }

    fun select(username: String?) {
        val normalized = username?.let(BotNames::normalize)
        if (selected.value.username == normalized) return
        val next = Selection(normalized, serial.incrementAndGet())
        selected.value = next
        _state.update { if (it.generation == next.serial) it else ConversationState(normalized,
            if (account.ready.value != null && normalized != null) ConversationStatus.LOADING else ConversationStatus.SIGN_IN, generation = next.serial) }
    }
    fun reload() {
        val next = selected.value.copy(serial = serial.incrementAndGet())
        selected.value = next
        _state.update { if (it.generation == next.serial) it else ConversationState(next.username, ConversationStatus.LOADING, generation = next.serial) }
    }
    fun dismissNotice() { _state.update { it.copy(notice = null, proposedUrl = null) } }
    fun takeConfirmedUrl(): String? {
        val active = binding ?: return null
        val snapshot = _state.value
        if (!valid(active) || snapshot.generation != active.selection.serial) return null
        val url = snapshot.proposedUrl?.let(::safeBotUrl) ?: return null
        return url.takeIf { _state.compareAndSet(snapshot, snapshot.copy(proposedUrl = null, notice = null)) && valid(active) }
    }
    suspend fun send(text: String): Boolean = perform { active ->
        if (text.isBlank() || text.length > 4096) throw TdFailure(FailureKind.BAD_INPUT)
        sendText(active, text)
    }
    suspend fun start(): Boolean = perform { active ->
        val message = active.account.rpc.request(TdJson.command("sendBotStartMessage") {
            put("bot_user_id", active.botId); put("chat_id", active.chatId); put("parameter", "")
        })
        if (valid(active)) { active.reducer.add(message); publish(active) }
    }
    suspend fun activate(ticket: ActionTicket): Boolean = perform { active ->
        val button = active.reducer.resolve(ticket) ?: throw TdFailure(FailureKind.WRONG_STATE)
        when (val payload = button.payload) {
            is ActionPayload.SendText -> {
                val usedKeyboard = active.reducer.keyboard
                sendText(active, payload.text)
                if (valid(active) && active.reducer.keyboardOneTime && usedKeyboard == active.reducer.keyboard) {
                    active.account.rpc.request(TdJson.command("deleteChatReplyMarkup") {
                        put("chat_id", active.chatId); put("message_id", ticket.messageId)
                    })
                    if (valid(active) && usedKeyboard == active.reducer.keyboard) { active.reducer.setKeyboard(null); publish(active) }
                }
            }
            is ActionPayload.Callback -> {
                val answer = active.account.rpc.request(TdJson.command("getCallbackQueryAnswer") {
                    put("chat_id", active.chatId); put("message_id", ticket.messageId)
                    put("payload", TdJson.command("callbackQueryPayloadData") { put("data", payload.opaqueData) })
                })
                change(active) { it.copy(notice = answer.string("text").take(4096).takeIf(String::isNotBlank), proposedUrl = safeBotUrl(answer.string("url"))) }
            }
            is ActionPayload.OpenUrl -> change(active) { it.copy(proposedUrl = safeBotUrl(payload.url)) }
            ActionPayload.Unsupported -> throw TdFailure(FailureKind.BAD_INPUT)
        }
    }
    private suspend fun sendText(active: Binding, text: String) {
        val message = active.account.rpc.request(TdJson.command("sendMessage") {
            put("chat_id", active.chatId)
            put("input_message_content", TdJson.command("inputMessageText") {
                put("text", TdJson.command("formattedText") { put("text", text); put("entities", JsonArray(emptyList())) })
                put("clear_draft", false)
            })
        })
        if (message.type() != "message") throw TdFailure(FailureKind.PROTOCOL)
        if (valid(active)) { active.reducer.add(message); publish(active) }
    }
    private fun publish(active: Binding) {
        change(active) { it.copy(timeline = active.reducer.timeline(), keyboard = active.reducer.keyboard) }
    }
    private fun change(active: Binding, transform: (ConversationState) -> ConversationState) {
        _state.update { if (valid(active) && it.generation == active.selection.serial) transform(it) else it }
    }
    private fun current(owner: ReadyAccount, selection: Selection) = account.ready.value === owner && selected.value == selection
    private fun valid(active: Binding) = binding === active && current(active.account, active.selection)
    private suspend fun perform(block: suspend (Binding) -> Unit): Boolean = withContext(engineDispatcher) {
        val active = binding ?: return@withContext false
        if (!valid(active) || active.faulted || _state.value.status != ConversationStatus.READY || !active.operation.tryLock()) return@withContext false
        change(active) { it.copy(busy = true, issue = null) }
        try { block(active); valid(active) }
        catch (_: TimeoutCancellationException) { change(active) { it.copy(issue = ConversationIssue.UNCERTAIN) }; false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: TdFailure) {
            change(active) { it.copy(issue = when (failure.kind) {
                FailureKind.WRONG_STATE -> ConversationIssue.STALE_ACTION
                FailureKind.BAD_INPUT -> ConversationIssue.UNSUPPORTED
                else -> ConversationIssue.CONNECTION
            }) }; false
        } catch (_: Exception) { change(active) { it.copy(issue = ConversationIssue.CONNECTION) }; false }
        finally { change(active) { it.copy(busy = false) }; active.operation.unlock() }
    }
}
