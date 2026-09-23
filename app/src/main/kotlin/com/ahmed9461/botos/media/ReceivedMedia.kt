package com.ahmed9461.botos.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahmed9461.botos.BotOsApplication
import com.ahmed9461.botos.model.*
import com.ahmed9461.botos.telegram.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

internal interface MediaFileAccess {
    val state: StateFlow<Map<RemoteFileKey, RemoteFileState>>
    fun key(chat: ChatKey, fileId: Int): RemoteFileKey?
    fun current(key: RemoteFileKey): Boolean
    fun request(key: RemoteFileKey, maximum: Long)
    fun cancel(key: RemoteFileKey)
}
internal class TelegramMediaAccess(private val files: TelegramFiles) : MediaFileAccess {
    override val state = files.state
    override fun key(chat: ChatKey, fileId: Int) = files.keyForChat(chat, fileId)
    override fun current(key: RemoteFileKey) = files.isCurrent(key)
    override fun request(key: RemoteFileKey, maximum: Long) = files.request(key, maximum)
    override fun cancel(key: RemoteFileKey) = files.cancel(key)
}
internal enum class MediaStage { IDLE, LOADING, READY, FAILED, CANCELLED }
internal data class PresentedMedia(val stage: MediaStage, val progress: Float? = null, val content: DecodedMedia? = null)
internal data class MediaSnapshot(val items: Map<MediaReference, PresentedMedia> = emptyMap(), val selected: MediaReference? = null)

/** Owns only a bounded set of requested previews; all bytes remain in TDLib's private directory. */
internal class ReceivedMediaController(
    private val root: File,
    private val conversations: StateFlow<ConversationState>,
    private val files: MediaFileAccess,
    scope: CoroutineScope,
) {
    private val work = CoroutineScope(scope.coroutineContext + Dispatchers.Main.immediate)
    private val wanted = MutableStateFlow<Map<MediaReference, RemoteFileKey>>(emptyMap())
    private val stateValue = MutableStateFlow(MediaSnapshot())
    val state = stateValue.asStateFlow()
    private val decoded = mutableMapOf<MediaReference, DecodedMedia>()
    private val failures = mutableSetOf<MediaReference>()
    private val cancelled = mutableSetOf<MediaReference>()
    private var indexedTimeline: MessageTimeline? = null
    private var index: Map<MediaReference, MediaInfo> = emptyMap()
    private val jobs = mutableMapOf<MediaReference, Job>()
    private val permits = Semaphore(2)

    init {
        work.launch {
            combine(conversations, files.state, wanted) { _, _, _ -> Unit }.collect { refresh() }
        }
    }

    private fun info(ref: MediaReference): MediaInfo? {
        val conversation = conversations.value
        if (conversation.status != ConversationStatus.READY || conversation.timeline?.chat != ref.chat) return null
        if (indexedTimeline !== conversation.timeline) {
            indexedTimeline = conversation.timeline
            index = messageMediaIndex(conversation.timeline)
        }
        return index[ref]?.takeIf(MediaDecoder::supported)
    }
    private fun valid(ref: MediaReference, key: RemoteFileKey): Boolean =
        wanted.value[ref] == key && files.current(key) && info(ref)?.fileId == key.fileId

    fun request(ref: MediaReference, automatic: Boolean = false) {
        val media = info(ref) ?: return
        val fileId = media.fileId ?: return
        if (automatic && (media.kind != MediaKind.PHOTO || (media.size ?: 0L) !in 1..MediaDecoder.AUTO_BYTES)) return
        val key = files.key(ref.chat, fileId) ?: return
        if (automatic && ref in wanted.value) return
        if (state.value.items[ref]?.stage == MediaStage.READY) return
        val next = wanted.value.toMutableMap()
        if (ref !in next && next.size >= MAX_PREVIEWS) {
            val victim = next.keys.firstOrNull { it != state.value.selected } ?: return
            next.remove(victim)
            jobs.remove(victim)?.cancel(); decoded.remove(victim); failures.remove(victim); cancelled.remove(victim)
        }
        next[ref] = key
        wanted.value = next
        failures.remove(ref)
        cancelled.remove(ref)
        if ((media.size ?: 0L) > MediaDecoder.limit(media)) {
            failures.add(ref); refresh(); return
        }
        files.request(key, if (automatic) MediaDecoder.AUTO_BYTES else MediaDecoder.limit(media))
        refresh()
    }
    fun cancel(ref: MediaReference) {
        val key = wanted.value[ref] ?: return
        if (!valid(ref, key)) return
        cancelled.add(ref)
        jobs.remove(ref)?.cancel()
        decoded.remove(ref)
        if (wanted.value.none { (other, file) -> other != ref && file == key && other !in cancelled }) files.cancel(key)
        stateValue.update { it.copy(items = it.items + (ref to PresentedMedia(MediaStage.CANCELLED)), selected = it.selected.takeUnless { keyRef -> keyRef == ref }) }
    }
    fun open(ref: MediaReference) {
        val key = wanted.value[ref] ?: return
        if (valid(ref, key) && decoded[ref] != null) stateValue.update { it.copy(selected = ref) }
    }
    fun close() { stateValue.update { it.copy(selected = null) } }
    fun leave() {
        close()
        wanted.value = emptyMap()
        val previousJobs = jobs.values.toList()
        jobs.clear(); decoded.clear(); failures.clear(); cancelled.clear()
        previousJobs.forEach { it.cancel() }
        stateValue.value = MediaSnapshot()
    }

    private fun refresh() {
        val requested = wanted.value
        val current = requested.filter { (ref, key) -> valid(ref, key) }
        if (current != requested) wanted.value = current
        jobs.keys.filter { it !in current }.forEach { jobs.remove(it)?.cancel() }
        decoded.keys.retainAll(current.keys)
        failures.retainAll(current.keys)
        cancelled.retainAll(current.keys)
        val presented = current.mapValues { (ref, key) ->
            val transfer = files.state.value[key]
            val payload = decoded[ref]
            when {
                ref in cancelled -> PresentedMedia(MediaStage.CANCELLED)
                ref in failures -> PresentedMedia(MediaStage.FAILED)
                transfer?.stage == TransferStage.CANCELLED -> PresentedMedia(MediaStage.CANCELLED)
                transfer?.stage == TransferStage.FAILED -> PresentedMedia(MediaStage.FAILED)
                payload != null -> PresentedMedia(MediaStage.READY, content = payload)
                transfer?.stage == TransferStage.READY && transfer.path == null -> PresentedMedia(MediaStage.FAILED)
                transfer?.stage == TransferStage.READY -> {
                    if (jobs[ref]?.isActive != true) decode(ref, key, transfer)
                    PresentedMedia(MediaStage.LOADING)
                }
                else -> PresentedMedia(MediaStage.LOADING, transfer?.takeIf { it.total > 0 }?.let {
                    (it.downloaded.toDouble() / it.total).toFloat().coerceIn(0f, 1f)
                })
            }
        }
        stateValue.update { it.copy(items = presented, selected = it.selected?.takeIf { ref -> presented[ref]?.stage == MediaStage.READY }) }
    }
    private fun decode(ref: MediaReference, key: RemoteFileKey, transfer: RemoteFileState) {
        val path = transfer.path ?: run { failures.add(ref); return }
        val media = info(ref) ?: return
        val job = work.launch(start = CoroutineStart.LAZY) {
            try {
                val value = permits.withPermit { withContext(Dispatchers.IO) {
                    check(files.current(key))
                    MediaDecoder.decode(root, path, media)
                } }
                ensureActive()
                if (valid(ref, key) && ref !in cancelled) decoded[ref] = value
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (valid(ref, key)) failures.add(ref) }
            finally {
                if (jobs[ref] === currentCoroutineContext()[Job]) jobs.remove(ref)
                if (valid(ref, key)) refresh()
            }
        }
        jobs[ref] = job
        job.start()
    }
    companion object { const val MAX_PREVIEWS = 8 }
}

internal class ReceivedMediaViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BotOsApplication
    val controller = ReceivedMediaController(File(application.noBackupFilesDir, "telegram/main/files"),
        app.botConversations.state, TelegramMediaAccess(app.telegramFiles), viewModelScope)
    val state = controller.state
}
