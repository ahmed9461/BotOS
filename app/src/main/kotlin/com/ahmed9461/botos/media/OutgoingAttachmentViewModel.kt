package com.ahmed9461.botos.media

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahmed9461.botos.BotOsApplication
import com.ahmed9461.botos.R
import com.ahmed9461.botos.telegram.runtime.AttachmentKind
import com.ahmed9461.botos.telegram.runtime.AttachmentTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class VoicePhase { PERMISSION, STARTING, RECORDING, FINISHING }
internal data class VoiceUiState(val target: AttachmentTarget, val phase: VoicePhase, val seconds: Int = 0)
internal enum class VoicePermissionResult { GRANTED, DENIED, CHANGED, IGNORED }

/** Permission callbacks cannot gain a fresh bot target after the user opened the dialog. */
internal class VoicePermissionGate(private val current: () -> AttachmentTarget?) {
    private var waiting: AttachmentTarget? = null
    fun request(): AttachmentTarget? {
        if (waiting != null) return null
        return current()?.also { waiting = it }
    }
    fun resolve(granted: Boolean): VoicePermissionResult {
        val fixed = waiting ?: return VoicePermissionResult.IGNORED
        waiting = null
        return when {
            !granted -> VoicePermissionResult.DENIED
            current() != fixed -> VoicePermissionResult.CHANGED
            else -> VoicePermissionResult.GRANTED
        }
    }
    fun clear() { waiting = null }
}

internal class OutgoingAttachmentViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BotOsApplication
    private val uploads = app.telegramUploads
    private val preparer = OutgoingAttachmentPreparer(application, uploads)
    private val capture: VoiceCapture = AndroidVoiceCapture(application)
    private val voiceSession = OutgoingVoiceSession(capture, uploads)
    private val permissionGate = VoicePermissionGate(uploads::captureTarget)
    val monitor = uploads.state
    private val _preview = MutableStateFlow<OutgoingPreview?>(null)
    val preview = _preview.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _notice = MutableStateFlow<Int?>(null)
    val notice = _notice.asStateFlow()
    private val _caption = MutableStateFlow("")
    val caption = _caption.asStateFlow()
    private val _voice = MutableStateFlow<VoiceUiState?>(null)
    val voice = _voice.asStateFlow()
    private var awaiting: Pair<AttachmentTarget, AttachmentKind>? = null
    private var voiceEpoch = 0L
    private var voiceWork: Job? = null
    private var voiceTimer: Job? = null
    private var closing: Job? = null

    init {
        viewModelScope.launch {
            app.botConversations.state.collect {
                val current = uploads.captureTarget()
                if (awaiting?.first != null && awaiting?.first != current) awaiting = null
                if (_voice.value?.target != null && _voice.value?.target != current) {
                    cancelVoice()
                    _notice.value = R.string.outgoing_target_changed
                }
                val old = _preview.value
                if (old != null && old.target != current) {
                    _preview.value = null
                    _caption.value = ""
                    discard(old)
                }
            }
        }
    }

    fun begin(kind: AttachmentKind): Boolean {
        if (_busy.value || _preview.value != null || awaiting != null || _voice.value != null ||
            voiceWork?.isActive == true || closing?.isActive == true || kind == AttachmentKind.VOICE) return false
        val target = uploads.captureTarget() ?: return false
        awaiting = target to kind
        _notice.value = null
        return true
    }

    fun pickerFailed() { awaiting = null; _notice.value = R.string.outgoing_unavailable }

    /** Fix the exact account, bot and chat before Android asks for microphone permission. */
    fun requestVoice(): Boolean {
        if (_busy.value || _preview.value != null || awaiting != null || _voice.value != null ||
            voiceWork?.isActive == true || closing?.isActive == true) return false
        val target = permissionGate.request() ?: return false
        _notice.value = null
        _voice.value = VoiceUiState(target, VoicePhase.PERMISSION)
        return true
    }

    fun voicePermission(granted: Boolean) {
        val pending = _voice.value?.takeIf { it.phase == VoicePhase.PERMISSION } ?: return
        when (permissionGate.resolve(granted)) {
            VoicePermissionResult.DENIED -> {
                _voice.value = null
                _notice.value = R.string.outgoing_microphone_denied
                return
            }
            VoicePermissionResult.CHANGED -> {
                _voice.value = null
                _notice.value = R.string.outgoing_target_changed
                return
            }
            VoicePermissionResult.IGNORED -> return
            VoicePermissionResult.GRANTED -> Unit
        }
        val token = ++voiceEpoch
        _voice.value = pending.copy(phase = VoicePhase.STARTING)
        voiceWork = viewModelScope.launch {
            try {
                capture.start()
                if (token == voiceEpoch && uploads.captureTarget() == pending.target) {
                    val started = SystemClock.elapsedRealtime()
                    _voice.value = pending.copy(phase = VoicePhase.RECORDING)
                    voiceTimer = viewModelScope.launch {
                        while (token == voiceEpoch && _voice.value?.phase == VoicePhase.RECORDING) {
                            delay(1_000)
                            val seconds = ((SystemClock.elapsedRealtime() - started) / 1_000).toInt()
                            if (seconds >= 55) { stopVoice(); break }
                            _voice.value = _voice.value?.copy(seconds = seconds)
                        }
                    }
                } else {
                    capture.abort()
                    if (token == voiceEpoch) {
                        _voice.value = null
                        _notice.value = R.string.outgoing_target_changed
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (token == voiceEpoch) {
                    _voice.value = null
                    _notice.value = R.string.outgoing_recording_failed
                }
                capture.abort()
            }
        }
    }

    fun stopVoice() {
        val active = _voice.value?.takeIf { it.phase == VoicePhase.RECORDING } ?: return
        val token = voiceEpoch
        voiceTimer?.cancel()
        _voice.value = active.copy(phase = VoicePhase.FINISHING)
        voiceWork = viewModelScope.launch {
            var prepared: OutgoingPreview? = null
            try {
                prepared = voiceSession.complete(active.target)
                if (token == voiceEpoch && uploads.captureTarget() == active.target) {
                    _preview.value = prepared
                    prepared = null
                } else if (token == voiceEpoch) _notice.value = R.string.outgoing_target_changed
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: VoiceTargetChanged) {
                if (token == voiceEpoch) _notice.value = R.string.outgoing_target_changed
            }
            catch (_: Exception) { if (token == voiceEpoch) _notice.value = R.string.outgoing_recording_failed }
            finally {
                withContext(NonCancellable) {
                    prepared?.let { try { uploads.discardPreview(it.staged) } catch (_: Exception) { /* Fail closed. */ } }
                }
                if (token == voiceEpoch) _voice.value = null
            }
        }
    }

    /** Abandon the microphone on leaving the foreground or changing the verified bot. */
    fun cancelVoice() {
        if (_voice.value == null) return
        voiceEpoch++
        permissionGate.clear()
        _voice.value = null
        voiceTimer?.cancel()
        val previous = voiceWork
        previous?.cancel()
        closing = viewModelScope.launch(Dispatchers.IO) {
            previous?.join()
            capture.abort()
        }
    }

    override fun onCleared() {
        capture.abort()
        super.onCleared()
    }

    fun picked(uri: Uri?) {
        val (target, kind) = awaiting.also { awaiting = null } ?: return
        if (uri == null) return
        if (uploads.captureTarget() != target) { _notice.value = R.string.outgoing_target_changed; return }
        _busy.value = true
        viewModelScope.launch {
            try {
                val prepared = preparer.prepare(target, uri, kind)
                if (uploads.captureTarget() == target) _preview.value = prepared
                else { discard(prepared); _notice.value = R.string.outgoing_target_changed }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = R.string.outgoing_unavailable }
            finally { _busy.value = false }
        }
    }

    fun editCaption(value: String) { if (!_busy.value) _caption.value = value.take(1024) }
    fun dismissNotice() { _notice.value = null }

    fun cancel() {
        if (_busy.value) return
        val previous = _preview.value ?: return
        _preview.value = null
        _caption.value = ""
        viewModelScope.launch { discard(previous) }
    }

    fun send() {
        val prepared = _preview.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                when (uploads.queue(prepared.target, prepared.staged, prepared.attachment, _caption.value)) {
                    is UploadQueueResult.Queued -> {
                        _preview.value = null
                        _caption.value = ""
                        _notice.value = R.string.outgoing_queued
                    }
                    UploadQueueResult.TargetChanged -> {
                        _preview.value = null
                        _caption.value = ""
                        discard(prepared)
                        _notice.value = R.string.outgoing_target_changed
                    }
                    UploadQueueResult.Unavailable -> _notice.value = R.string.outgoing_unavailable
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = R.string.outgoing_unavailable }
            finally { _busy.value = false }
        }
    }

    private suspend fun discard(prepared: OutgoingPreview) {
        try { uploads.discardPreview(prepared.staged) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* The journal may already own this input. Never delete it by path. */ }
    }
}
