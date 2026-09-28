package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.ChatKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

data class RemoteFileKey(val accountId: Long, val generation: Long, val fileId: Int) {
    override fun toString() = "RemoteFileKey([redacted])"
}
enum class TransferStage { QUEUED, DOWNLOADING, READY, FAILED, CANCELLED }
data class RemoteFileState(val stage: TransferStage, val downloaded: Long = 0, val total: Long = 0,
    val path: String? = null) {
    override fun toString() = "RemoteFileState(stage=$stage)"
}
internal fun fileState(raw: JsonObject): RemoteFileState {
    require(raw.type() == "file")
    val local = raw.obj("local")
    val size = raw.number("size")?.coerceAtLeast(0) ?: 0L
    val total = size.takeIf { it > 0 } ?: raw.number("expected_size")?.coerceAtLeast(0) ?: 0L
    val downloaded = local?.number("downloaded_size")?.coerceAtLeast(0) ?: 0L
    val path = local?.string("path")?.takeIf { it.startsWith('/') && it.none(Char::isISOControl) }
    return if (local?.flag("is_downloading_completed") == true && path != null) {
        RemoteFileState(TransferStage.READY, downloaded, total, path)
    } else RemoteFileState(TransferStage.DOWNLOADING, downloaded, total)
}

/** Account-scoped shared downloads. No arbitrary HTTP fetcher. */
class TelegramFiles(private val account: AccountCoordinator, scope: CoroutineScope) {
    private val engine = CoroutineScope(scope.coroutineContext + Dispatchers.IO.limitedParallelism(1))
    private val permits = Semaphore(3)
    private val running = mutableMapOf<RemoteFileKey, Job>()
    private val limits = mutableMapOf<RemoteFileKey, Long>()
    private val versions = mutableMapOf<RemoteFileKey, Long>()
    private val _state = MutableStateFlow<Map<RemoteFileKey, RemoteFileState>>(emptyMap())
    val state = _state.asStateFlow()
    @Volatile private var bound: ReadyAccount? = null
    private val bindingSignal = MutableStateFlow<ReadyAccount?>(null)
    init {
        engine.launch {
            account.ready.collectLatest { owner ->
                bound = owner
                running.values.forEach { it.cancel() }; running.clear(); limits.clear(); versions.clear()
                _state.value = emptyMap()
                bindingSignal.value = owner
                if (owner == null) return@collectLatest
                val observer = owner.rpc.observeUpdates { update ->
                    val file = update.takeIf { it.type() == "updateFile" }?.obj("file") ?: return@observeUpdates
                    val id = file.number("id")?.toInt() ?: return@observeUpdates
                    val key = RemoteFileKey(owner.userId, owner.generation, id)
                    if (key !in _state.value) return@observeUpdates
                    engine.launch {
                        if (current(owner, key) && key in limits) {
                            versions[key] = (versions[key] ?: 0) + 1
                            accept(owner, key, file)
                        }
                    }
                }
                try { awaitCancellation() } finally { observer.close() }
            }
        }
    }
    fun key(fileId: Int): RemoteFileKey? {
        val owner = account.ready.value ?: return null
        return if (fileId > 0) RemoteFileKey(owner.userId, owner.generation, fileId) else null
    }
    /** Reject the brief interval in which UI still holds the previous account's timeline. */
    fun keyForChat(chat: ChatKey, fileId: Int): RemoteFileKey? {
        val owner = account.ready.value ?: return null
        if (chat.account != "${owner.userId}:${owner.generation}") return null
        return fileId.takeIf { it > 0 }?.let { RemoteFileKey(owner.userId, owner.generation, it) }
    }
    fun isCurrent(key: RemoteFileKey): Boolean {
        val owner = account.ready.value ?: return false
        return owner.userId == key.accountId && owner.generation == key.generation
    }
    fun request(key: RemoteFileKey, maxBytes: Long = MAX_DOWNLOAD) {
        if (maxBytes !in 1..MAX_DOWNLOAD) return
        engine.launch {
            val owner = account.ready.value ?: return@launch
            if (owner.userId != key.accountId || owner.generation != key.generation) return@launch
            withTimeoutOrNull(5_000) { bindingSignal.first { it === owner || account.ready.value !== owner } }
            if (!current(owner, key) || running[key]?.isActive == true || _state.value[key]?.stage == TransferStage.READY) return@launch
            if (_state.value.size >= 256 && key !in _state.value) {
                val victim = _state.value.keys.firstOrNull { running[it]?.isActive != true } ?: return@launch
                _state.update { it - victim }; limits.remove(victim); versions.remove(victim)
            }
            limits[key] = maxBytes
            _state.update { it + (key to RemoteFileState(TransferStage.QUEUED)) }
            val job = engine.launch(start = CoroutineStart.LAZY) {
                try {
                    permits.withPermit {
                        if (!current(owner, key)) return@withPermit
                        val before = versions[key] ?: 0
                        val known = owner.rpc.request(TdJson.command("getFile") { put("file_id", key.fileId) })
                        if (!current(owner, key)) return@withPermit
                        if ((versions[key] ?: 0) == before) accept(owner, key, known)
                        val previous = _state.value[key]
                        if (previous?.stage in setOf(TransferStage.READY, TransferStage.FAILED)) return@withPermit
                        val token = versions[key] ?: 0
                        val download = owner.rpc.request(TdJson.command("downloadFile") {
                            put("file_id", key.fileId); put("priority", 16); put("offset", 0); put("limit", 0); put("synchronous", false)
                        })
                        if (!current(owner, key)) return@withPermit
                        if ((versions[key] ?: 0) == token) accept(owner, key, download)
                        withTimeout(120_000) {
                            state.first { !current(owner, key) || it[key]?.stage in setOf(TransferStage.READY, TransferStage.FAILED, TransferStage.CANCELLED) }
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    if (current(owner, key)) { mark(key, TransferStage.FAILED); stopDownload(owner, key) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (current(owner, key)) mark(key, TransferStage.FAILED) }
                finally { if (running[key] === currentCoroutineContext()[Job]) running.remove(key) }
            }
            running[key] = job
            job.start()
        }
    }
    fun cancel(key: RemoteFileKey) {
        engine.launch {
            val owner = bound ?: return@launch
            if (!current(owner, key)) return@launch
            running.remove(key)?.cancel()
            mark(key, TransferStage.CANCELLED)
            stopDownload(owner, key)
        }
    }
    private fun accept(owner: ReadyAccount, key: RemoteFileKey, raw: JsonObject) {
        if (!current(owner, key) || raw.number("id") != key.fileId.toLong()) return
        val value = runCatching { fileState(raw) }.getOrElse { mark(key, TransferStage.FAILED); return }
        val maximum = limits[key] ?: return
        if (maxOf(value.total, value.downloaded) > maximum) {
            mark(key, TransferStage.FAILED)
            engine.launch { stopDownload(owner, key) }
        } else if (_state.value[key]?.stage != TransferStage.CANCELLED) _state.update { it + (key to value) }
    }
    private fun mark(key: RemoteFileKey, stage: TransferStage) {
        _state.update { it + (key to (it[key] ?: RemoteFileState(stage)).copy(stage = stage, path = null)) }
    }
    private suspend fun stopDownload(owner: ReadyAccount, key: RemoteFileKey) {
        if (!current(owner, key)) return
        try { owner.rpc.request(TdJson.command("cancelDownloadFile") { put("file_id", key.fileId); put("only_if_pending", false) }) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
    }
    private fun current(owner: ReadyAccount, key: RemoteFileKey) = bound === owner && account.ready.value === owner &&
        key.accountId == owner.userId && key.generation == owner.generation
    companion object { const val MAX_DOWNLOAD = 256L * 1024 * 1024 }
}
