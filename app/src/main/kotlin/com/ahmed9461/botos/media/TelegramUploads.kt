package com.ahmed9461.botos.media

import com.ahmed9461.botos.data.OutgoingMediaStore
import com.ahmed9461.botos.data.OutgoingUploadJournal
import com.ahmed9461.botos.data.OutgoingUploadRecord
import com.ahmed9461.botos.data.StagedOutgoingMedia
import com.ahmed9461.botos.data.UploadStatus
import com.ahmed9461.botos.telegram.runtime.AttachmentGateway
import com.ahmed9461.botos.telegram.runtime.AttachmentSendResult
import com.ahmed9461.botos.telegram.runtime.AttachmentTarget
import com.ahmed9461.botos.telegram.runtime.PreparedAttachment
import com.ahmed9461.botos.telegram.runtime.UploadEvent
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class UploadMonitorState(val records: List<OutgoingUploadRecord> = emptyList(), val storageError: Boolean = false) {
    override fun toString() = "UploadMonitorState(count=${records.size}, storageError=$storageError)"
}

sealed interface UploadQueueResult {
    data class Queued(val id: String) : UploadQueueResult
    data object TargetChanged : UploadQueueResult
    data object Unavailable : UploadQueueResult
}

/** Application-owned: a tab or activity closing cannot cancel an accepted upload. */
class TelegramUploads(
    private val gateway: AttachmentGateway,
    private val media: OutgoingMediaStore,
    private val journal: OutgoingUploadJournal,
    applicationScope: CoroutineScope,
) {
    private val scope = CoroutineScope(applicationScope.coroutineContext + Dispatchers.IO)
    private val queueLock = Mutex()
    private val random = SecureRandom()
    private val storageReady = CompletableDeferred<Boolean>()
    private val _state = MutableStateFlow(UploadMonitorState())
    val state: StateFlow<UploadMonitorState> = _state.asStateFlow()

    init {
        // Subscribe before a UI can queue its first send; TDLib callbacks never write disk on their thread.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            gateway.uploadEvents.collect { event ->
                if (gateway.accountKey.value == event.accountKey) guarded { applyEvent(event) }
            }
        }
        scope.launch {
            try {
                journal.discardOrphanPreviews()
                refresh()
                storageReady.complete(true)
            } catch (cancelled: CancellationException) { storageReady.cancel(); throw cancelled }
            catch (_: Exception) {
                _state.value = _state.value.copy(storageError = true)
                storageReady.complete(false)
            }
        }
        scope.launch {
            gateway.accountKey.collectLatest { key -> if (key != null) guarded { recover(key) } }
        }
    }

    fun captureTarget(): AttachmentTarget? = gateway.captureAttachmentTarget()
    suspend fun stage(fileName: String? = null, open: () -> InputStream): StagedOutgoingMedia {
        if (!storageReady.await()) throw IOException("Attachment journal unavailable")
        return media.stage(fileName, open)
    }
    suspend fun discardPreview(staged: StagedOutgoingMedia) = journal.discardPreview(staged)

    /** Queued means durably held before RPC; it never claims Telegram delivery. */
    suspend fun queue(target: AttachmentTarget, staged: StagedOutgoingMedia,
        attachment: PreparedAttachment, caption: String): UploadQueueResult = withContext(Dispatchers.IO) {
        queueLock.withLock {
            if (!storageReady.await()) return@withLock UploadQueueResult.Unavailable
            if (gateway.captureAttachmentTarget() != target || gateway.accountKey.value != target.chat.account) {
                return@withLock UploadQueueResult.TargetChanged
            }
            if (staged.path != attachment.path || staged.bytes != attachment.bytes) return@withLock UploadQueueResult.Unavailable
            val reserved = try {
                if (journal.snapshot().any { it.mediaId == staged.id }) return@withLock UploadQueueResult.Unavailable
                var entry: OutgoingUploadRecord? = null
                repeat(8) {
                    if (entry == null) {
                        val id = random.nextInt(Int.MAX_VALUE - 1) + 1
                        if (journal.snapshot().none { it.sendingId == id }) entry = journal.reserve(target, staged, id)
                    }
                }
                entry ?: return@withLock UploadQueueResult.Unavailable
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _state.value = _state.value.copy(storageError = true)
                return@withLock UploadQueueResult.Unavailable
            }
            refresh()
            scope.launch { attempt(reserved, target, attachment, caption) }
            UploadQueueResult.Queued(reserved.id)
        }
    }

    private suspend fun attempt(record: OutgoingUploadRecord, target: AttachmentTarget,
        attachment: PreparedAttachment, caption: String) {
        var attempted = false
        try {
            // The picker can return after a bot switch; no RPC for an obsolete target.
            if (gateway.captureAttachmentTarget() != target || gateway.accountKey.value != record.accountKey) {
                journal.discardStaged(record.id)
                refresh()
                return
            }
            journal.beforeRpc(record.id)
            attempted = true
            refresh()
            when (val result = gateway.sendAttachment(target, attachment, caption, record.sendingId)) {
                is AttachmentSendResult.Pending -> journal.pending(record.accountKey, record.chatId,
                    record.sendingId, result.temporaryMessageId)
                is AttachmentSendResult.Uncertain, AttachmentSendResult.Rejected -> journal.uncertain(record.id)
            }
            refresh()
        } catch (cancelled: CancellationException) {
            if (attempted) withContext(NonCancellable) { guarded { journal.uncertain(record.id); refresh() } }
            throw cancelled
        } catch (_: Exception) {
            if (attempted) guarded { journal.uncertain(record.id); refresh() }
            else _state.value = _state.value.copy(storageError = true)
        }
    }

    private suspend fun applyEvent(event: UploadEvent) {
        val record = when (event) {
            is UploadEvent.Pending -> journal.pending(event.accountKey, event.chatId,
                event.sendingId, event.temporaryMessageId)
            is UploadEvent.Succeeded -> journal.succeeded(event.accountKey, event.chatId,
                event.sendingId, event.temporaryMessageId, event.messageId)
            is UploadEvent.Failed -> journal.failed(event.accountKey, event.chatId,
                event.sendingId, event.temporaryMessageId)
        }
        if (record?.status == UploadStatus.SUCCEEDED) journal.releaseSucceeded(record.id)
        refresh()
    }

    /** Never calls sendMessage. A known old temporary ID is probed only for the same Telegram user. */
    private suspend fun recover(current: String) {
        val currentUser = current.substringBefore(':').toLongOrNull() ?: return
        for (record in journal.snapshot()) {
            if (record.accountKey.substringBefore(':').toLongOrNull() != currentUser ||
                record.status !in setOf(UploadStatus.PENDING, UploadStatus.UNKNOWN) ||
                record.temporaryMessageId == 0L || gateway.accountKey.value != current) continue
            val result = gateway.inspectAttachment(current, record.chatId, record.temporaryMessageId,
                record.sendingId) ?: continue
            if (gateway.accountKey.value != current || result.accountKey != current ||
                result.chatId != record.chatId || result.sendingId != record.sendingId) continue
            // The record retains its original generation. The fresh probe is bound to the ready account.
            val scoped = when (result) {
                is UploadEvent.Pending -> result.copy(accountKey = record.accountKey)
                is UploadEvent.Succeeded -> result.copy(accountKey = record.accountKey)
                is UploadEvent.Failed -> result.copy(accountKey = record.accountKey)
            }
            applyEvent(scoped)
        }
        refresh()
    }

    private suspend fun refresh() { _state.value = UploadMonitorState(journal.snapshot()) }
    private suspend fun guarded(block: suspend () -> Unit) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.value = _state.value.copy(storageError = true) }
    }
}
