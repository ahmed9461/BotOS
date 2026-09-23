package com.ahmed9461.botos.media

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahmed9461.botos.BotOsApplication
import com.ahmed9461.botos.R
import com.ahmed9461.botos.model.SavedBot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class AvatarViewModel(application: Application) : AndroidViewModel(application) {
    private val avatars = (application as BotOsApplication).botAvatars
    val state = avatars.state
    private val _target = MutableStateFlow<SavedBot?>(null)
    val target = _target.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val messages = Channel<Int>(Channel.BUFFERED)
    val notices = messages.receiveAsFlow()
    // Target survives Activity recreation via ViewModel, but no old URI/target is revived after process death.
    private var pickerTarget: SavedBot? = null
    fun configure(bot: SavedBot) { if (!_busy.value && pickerTarget == null && avatars.contains(bot)) _target.value = bot }
    fun dismiss() { if (!_busy.value) _target.value = null }
    fun preparePicker(): Boolean {
        val bot = _target.value ?: return false
        if (_busy.value || pickerTarget != null || !avatars.contains(bot)) return false
        pickerTarget = bot
        _target.value = null
        return true
    }
    fun pickerFailed() { pickerTarget = null; messages.trySend(R.string.avatar_error) }
    fun picked(uri: Uri?) {
        val bot = pickerTarget.also { pickerTarget = null } ?: return
        if (uri == null) return
        mutate { avatars.import(bot, uri) }
    }
    fun reset() {
        val bot = _target.value ?: return
        mutate { avatars.reset(bot) }
    }
    private fun mutate(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { block(); _target.value = null; messages.send(R.string.avatar_saved) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { messages.send(R.string.avatar_error) }
            finally { _busy.value = false }
        }
    }
}
