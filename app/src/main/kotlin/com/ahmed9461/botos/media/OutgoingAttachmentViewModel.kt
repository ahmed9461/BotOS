package com.ahmed9461.botos.media

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahmed9461.botos.BotOsApplication
import com.ahmed9461.botos.R
import com.ahmed9461.botos.telegram.runtime.AttachmentKind
import com.ahmed9461.botos.telegram.runtime.AttachmentTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class OutgoingAttachmentViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BotOsApplication
    private val uploads = app.telegramUploads
    private val preparer = OutgoingAttachmentPreparer(application, uploads)
    val monitor = uploads.state
    private val _preview = MutableStateFlow<OutgoingPreview?>(null)
    val preview = _preview.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _notice = MutableStateFlow<Int?>(null)
    val notice = _notice.asStateFlow()
    private val _caption = MutableStateFlow("")
    val caption = _caption.asStateFlow()
    private var awaiting: Pair<AttachmentTarget, AttachmentKind>? = null

    init {
        viewModelScope.launch {
            app.botConversations.state.collect {
                val current = uploads.captureTarget()
                if (awaiting?.first != null && awaiting?.first != current) awaiting = null
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
        if (_busy.value || _preview.value != null || awaiting != null || kind == AttachmentKind.VOICE) return false
        val target = uploads.captureTarget() ?: return false
        awaiting = target to kind
        _notice.value = null
        return true
    }

    fun pickerFailed() { awaiting = null; _notice.value = R.string.outgoing_unavailable }

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
