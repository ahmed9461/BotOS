package com.ahmed9461.botos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahmed9461.botos.telegram.runtime.AuthStep
import kotlinx.coroutines.launch

/** Sensitive inputs are passed transiently; no SavedStateHandle, input properties, or logging. */
class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val account = (application as BotOsApplication).account
    val state = account.state
    fun connect(consent: Boolean) { viewModelScope.launch { account.connect(consent) } }
    fun submit(step: AuthStep, input: String) { viewModelScope.launch { account.submit(step, input) } }
    fun cancelLogin() { viewModelScope.launch { account.cancelLogin() } }
    fun retryIdentity() { viewModelScope.launch { account.retryIdentity() } }
    fun logOut() { viewModelScope.launch { account.logOut() } }
}
