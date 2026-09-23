package com.ahmed9461.botos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahmed9461.botos.model.ActionTicket
import com.ahmed9461.botos.model.ChatKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LiveBotViewModel(application: Application) : AndroidViewModel(application) {
    private val bots = (application as BotOsApplication).botConversations
    val state = bots.state
    private val text = MutableStateFlow("")
    val draft = text.asStateFlow()
    init {
        viewModelScope.launch {
            bots.state.map { it.timeline?.chat }.distinctUntilChanged().collect { text.value = "" }
        }
    }
    fun select(username: String?) { bots.select(username) }
    fun edit(value: String) { text.value = value.take(4096) }
    fun reload() { bots.reload() }
    fun start() { viewModelScope.launch { bots.start() } }
    fun send() {
        val value = text.value
        val chat = state.value.timeline?.chat
        viewModelScope.launch {
            if (bots.send(value) && state.value.timeline?.chat == chat && text.value == value) text.value = ""
        }
    }
    fun stopPending(chat: ChatKey, draftId: Long) { viewModelScope.launch { bots.stopPending(draftId, chat) } }
    fun activate(ticket: ActionTicket) { viewModelScope.launch { bots.activate(ticket) } }
    fun dismiss() { bots.dismissNotice() }
    fun confirmedUrl() = bots.takeConfirmedUrl()
}
