package com.ahmed9461.botos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ahmed9461.botos.data.*
import com.ahmed9461.botos.model.*
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
    val workspace = store.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoreSnapshot(loading = true))
    // An empty selection is the real chat list. Old preview selections naturally resolve to it.
    val selected = saved.getStateFlow("selectedBot", HOME)
    init { saved.remove<String>("previewDraft") }
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val channel = Channel<UiEffect>(Channel.BUFFERED)
    val effects = channel.receiveAsFlow()
    fun select(id: String) { saved["selectedBot"] = id }
    fun saveBot(id: String?, username: String, title: String, origin: String) = mutate {
        val result = store.saveBot(id, username, title)
        select(result)
        channel.send(UiEffect.Saved(result, origin))
    }
    fun remove(id: String) = mutate { store.deleteBot(id); if (selected.value == id) select(HOME) }
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
    companion object { const val HOME = "" }
}
