package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.BotNames
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

/** Resolve only saved, verified bots. No openChat, /start, or external avatar service. */
class BotProfiles(private val account: AccountCoordinator, private val files: TelegramFiles, scope: CoroutineScope) {
    private val names = MutableStateFlow<Set<String>>(emptySet())
    private val refreshes = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _photos = MutableStateFlow<Map<String, RemoteFileKey>>(emptyMap())
    val photos = _photos.asStateFlow()
    init {
        scope.launch(Dispatchers.IO.limitedParallelism(1)) {
            combine(account.ready, names) { owner, wanted -> owner to wanted }.collectLatest { (owner, wanted) ->
                // Adding/reordering a bookmark must not flash every existing avatar back to a letter.
                _photos.update { previous -> previous.filter { (name, key) -> name in wanted && files.isCurrent(key) } }
                if (owner == null) { _photos.value = emptyMap(); return@collectLatest }
                coroutineScope {
                    val verified = mutableMapOf<Long, String>()
                    val chatUsers = mutableMapOf<Long, String>()
                    val resolving = mutableMapOf<String, Job>()
                    val manualTimes = mutableMapOf<String, Long>()
                    val downloadAttempts = mutableMapOf<RemoteFileKey, Int>()
                    val permits = Semaphore(2)
                    fun current() = account.ready.value === owner && names.value == wanted
                    fun photo(name: String, raw: JsonObject?) {
                        if (!current()) return
                        val id = raw?.obj("small")?.number("id")?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
                        val key = id?.let(files::key)
                        if (key == null) _photos.update { it - name }
                        else { _photos.update { it + (name to key) }; files.request(key, AVATAR_BYTES) }
                    }
                    val observer = owner.rpc.observeUpdates { update ->
                        launch {
                            if (!current()) return@launch
                            when (update.type()) {
                                "updateUser" -> {
                                    val user = update.obj("user") ?: return@launch
                                    val name = user.number("id")?.let(verified::get) ?: return@launch
                                    if (user.obj("type")?.type() == "userTypeBot") photo(name, user.obj("profile_photo"))
                                    else _photos.update { it - name }
                                }
                                "updateChatPhoto" -> {
                                    val name = update.number("chat_id")?.let(chatUsers::get) ?: return@launch
                                    photo(name, update.obj("photo"))
                                }
                            }
                        }
                    }
                    suspend fun resolve(name: String) {
                        for (attempt in 0..2) {
                            if (attempt > 0) delay(if (attempt == 1) 400L else 1_200L)
                            if (!current()) return
                            try {
                                permits.withPermit {
                                    val chat = owner.rpc.request(TdJson.command("searchPublicChat") { put("username", name) })
                                    if (!current()) return@withPermit
                                    val type = chat.obj("type")
                                    val userId = type?.number("user_id")
                                    if (chat.type() != "chat" || type?.type() != "chatTypePrivate" || userId == null) {
                                        _photos.update { it - name }; return@withPermit
                                    }
                                    val user = owner.rpc.request(TdJson.command("getUser") { put("user_id", userId) })
                                    if (!current()) return@withPermit
                                    if (user.number("id") != userId || user.obj("type")?.type() != "userTypeBot") {
                                        _photos.update { it - name }; return@withPermit
                                    }
                                    verified[userId] = name
                                    chat.number("id")?.let { chatUsers[it] = name }
                                    photo(name, user.obj("profile_photo") ?: chat.obj("photo"))
                                }
                                return
                            } catch (_: TimeoutCancellationException) {
                                currentCoroutineContext().ensureActive()
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: TdFailure) {
                                // Invalid handles, authentication and rate limits are not retry loops.
                                if (failure.kind != FailureKind.REMOTE || (failure.code ?: 0) !in 500..599) return
                            } catch (_: Exception) { /* A bounded retry for transient transport failures. */ }
                        }
                    }
                    fun enqueue(name: String) {
                        if (name !in wanted || resolving[name]?.isActive == true) return
                        resolving[name] = launch { resolve(name) }
                    }
                    try {
                        launch {
                            refreshes.collect { name ->
                                val now = System.nanoTime()
                                val last = manualTimes[name]
                                if (name in wanted && (last == null || now - last >= 3_000_000_000L)) {
                                    manualTimes[name] = now; enqueue(name)
                                }
                            }
                        }
                        launch {
                            files.state.collect { transfers ->
                                if (!current()) return@collect
                                _photos.value.values.toSet().forEach { key ->
                                    val count = downloadAttempts[key] ?: 0
                                    if (transfers[key]?.stage == TransferStage.FAILED && count < 2) {
                                        downloadAttempts[key] = count + 1
                                        launch {
                                            delay(if (count == 0) 1_500L else 4_000L)
                                            if (current() && key in _photos.value.values && files.isCurrent(key) &&
                                                files.state.value[key]?.stage == TransferStage.FAILED) files.request(key, AVATAR_BYTES)
                                        }
                                    }
                                }
                            }
                        }
                        wanted.forEach(::enqueue)
                        awaitCancellation()
                    } finally { observer.close() }
                }
            }
        }
    }
    fun observe(usernames: Collection<String>) { names.value = usernames.mapNotNull(BotNames::normalize).take(100).toSet() }
    fun refresh(username: String) {
        val name = BotNames.normalize(username) ?: return
        if (name in names.value) refreshes.tryEmit(name)
    }
    private companion object { const val AVATAR_BYTES = 2L * 1024 * 1024 }
}
