package com.ahmed9461.botos.telegram.runtime

import com.ahmed9461.botos.model.BotNames
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

/** Resolve saved usernames only, without opening their chats or sending /start. */
class BotProfiles(private val account: AccountCoordinator, private val files: TelegramFiles, scope: CoroutineScope) {
    private val names = MutableStateFlow<Set<String>>(emptySet())
    private val _photos = MutableStateFlow<Map<String, RemoteFileKey>>(emptyMap())
    val photos = _photos.asStateFlow()
    init {
        scope.launch(Dispatchers.IO.limitedParallelism(1)) {
            combine(account.ready, names) { owner, wanted -> owner to wanted }.collectLatest { (owner, wanted) ->
                _photos.value = emptyMap()
                if (owner == null) return@collectLatest
                coroutineScope {
                    val verified = mutableMapOf<Long, String>()
                    val chatUsers = mutableMapOf<Long, String>()
                    fun photo(name: String, raw: JsonObject?) {
                        if (account.ready.value !== owner || names.value != wanted) return
                        val id = raw?.obj("small")?.number("id")?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
                        val key = id?.let(files::key)
                        if (key == null) _photos.update { it - name }
                        else { _photos.update { it + (name to key) }; files.request(key, 2L * 1024 * 1024) }
                    }
                    val observer = owner.rpc.observeUpdates { update ->
                        launch {
                            if (account.ready.value !== owner || names.value != wanted) return@launch
                            if (update.type() == "updateUser") {
                                val user = update.obj("user") ?: return@launch
                                val name = user.number("id")?.let(verified::get) ?: return@launch
                                if (user.obj("type")?.type() == "userTypeBot") photo(name, user.obj("profile_photo"))
                                else _photos.update { it - name }
                            } else if (update.type() == "updateChatPhoto") {
                                val name = update.number("chat_id")?.let(chatUsers::get) ?: return@launch
                                photo(name, update.obj("photo"))
                            }
                        }
                    }
                    try {
                        val permits = Semaphore(2)
                        wanted.take(100).forEach { name -> launch {
                            permits.withPermit {
                                try {
                                    val chat = owner.rpc.request(TdJson.command("searchPublicChat") { put("username", name) })
                                    if (account.ready.value !== owner || names.value != wanted) return@withPermit
                                    val type = chat.obj("type")
                                    val userId = type?.number("user_id")
                                    if (chat.type() != "chat" || type?.type() != "chatTypePrivate" || userId == null) return@withPermit
                                    val user = owner.rpc.request(TdJson.command("getUser") { put("user_id", userId) })
                                    if (account.ready.value !== owner || names.value != wanted) return@withPermit
                                    if (user.number("id") != userId || user.obj("type")?.type() != "userTypeBot") return@withPermit
                                    verified[userId] = name
                                    chat.number("id")?.let { chatUsers[it] = name }
                                    photo(name, user.obj("profile_photo") ?: chat.obj("photo"))
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { /* Keep the local bookmark and fallback badge. */ }
                            }
                        } }
                        awaitCancellation()
                    } finally { observer.close() }
                }
            }
        }
    }
    fun observe(usernames: Collection<String>) { names.value = usernames.mapNotNull(BotNames::normalize).take(100).toSet() }
}
