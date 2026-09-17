package com.ahmed9461.botos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ahmed9461.botos.data.*
import com.ahmed9461.botos.model.*
import com.ahmed9461.botos.telegram.PreviewGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface UiEffect {
    data class Notice(val stringId: Int) : UiEffect
    data class Saved(val id: String, val origin: String) : UiEffect
}
class WorkspaceViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val store = WorkspaceStore(application)
    private val gateway = PreviewGateway()
    private val previewChat = ChatKey("preview", "showcase")
    val workspace = store.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoreSnapshot(loading = true))
    val selected = saved.getStateFlow("selectedBot", PREVIEW)
    val draft = saved.getStateFlow("previewDraft", "")
    private val _timeline = MutableStateFlow(MessageTimeline(previewChat).upsert(gateway.initial(previewChat)))
    val timeline = _timeline.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val channel = Channel<UiEffect>(Channel.BUFFERED)
    val effects = channel.receiveAsFlow()
    private var nextMessageId = 2L

    fun select(id: String) { saved["selectedBot"] = id }
    fun updateDraft(value: String) { saved["previewDraft"] = value.take(4_000) }
    fun sendPreview(reply: String) {
        val text = draft.value.trim()
        if (text.isEmpty()) return
        val outgoingId = nextMessageId++
        val replyId = nextMessageId++
        _timeline.update { old -> old
            .upsert(BotMessage(outgoingId, previewChat, 1, listOf(Block.Paragraph("text", text)), outgoing = true))
            .upsert(BotMessage(replyId, previewChat, 1, listOf(Block.Paragraph("text", reply)))) }
        updateDraft("")
    }
    fun activate(ticket: ActionTicket) {
        val current = _timeline.value
        val button = current.resolve(ticket)
        val message = current.messages.firstOrNull { it.id == ticket.messageId }
        val changed = if (button != null && message != null) gateway.activate(message, button) else null
        if (changed == null) notice(R.string.stale_action) else _timeline.update { it.upsert(changed) }
    }
    fun saveBot(id: String?, username: String, title: String, origin: String) = mutate {
        val result = store.saveBot(id, username, title)
        select(result)
        channel.send(UiEffect.Saved(result, origin))
    }
    fun remove(id: String) = mutate { store.deleteBot(id); if (selected.value == id) select(PREVIEW) }
    fun move(id: String, delta: Int) = mutate { store.moveBot(id, delta) }
    fun setTheme(theme: ThemeMode) = mutate { store.setTheme(theme) }
    fun setMotion(reduce: Boolean) = mutate { store.setReducedMotion(reduce) }
    fun notice(id: Int) { channel.trySend(UiEffect.Notice(id)) }
    private fun mutate(action: suspend () -> Unit) {
        if (_busy.value || workspace.value.loading || workspace.value.failed) return
        _busy.value = true
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: DuplicateBookmark) { notice(R.string.duplicate_bot) }
            catch (_: InvalidBookmark) { notice(R.string.invalid_bot) }
            catch (_: BookmarkLimit) { notice(R.string.bot_limit) }
            catch (_: Exception) { notice(R.string.save_error) }
            finally { _busy.value = false }
        }
    }
    companion object { const val PREVIEW = "preview-showcase" }
}
