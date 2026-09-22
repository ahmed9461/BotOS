package com.ahmed9461.botos.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.ahmed9461.botos.data.AvatarDecoder
import com.ahmed9461.botos.data.LocalAvatarStore
import com.ahmed9461.botos.data.WorkspaceStore
import com.ahmed9461.botos.model.SavedBot
import com.ahmed9461.botos.telegram.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

internal data class AvatarSnapshot(val images: Map<String, Bitmap> = emptyMap(), val custom: Set<String> = emptySet()) {
    override fun toString() = "AvatarSnapshot(count=${images.size})"
}
private sealed interface AvatarSource {
    data class Local(val key: String, val version: String) : AvatarSource
    data class Remote(val key: RemoteFileKey, val path: String) : AvatarSource
}

/** One process-owned coordinator. Renderers get pixels, not paths, accounts or network clients. */
internal class BotAvatars(context: Context, account: AccountCoordinator, files: TelegramFiles,
    profiles: BotProfiles, scope: CoroutineScope) {
    private val application = context.applicationContext
    private val store = LocalAvatarStore(File(application.noBackupFilesDir, "bot-avatars"))
    private val bookmarks = MutableStateFlow<List<SavedBot>>(emptyList())
    private val _state = MutableStateFlow(AvatarSnapshot())
    val state = _state.asStateFlow()
    private val decodePermits = Semaphore(2)
    private val remoteRoot by lazy { File(application.noBackupFilesDir, "telegram/main/files").canonicalFile }

    init {
        scope.launch {
            WorkspaceStore(application).state.filter { !it.loading && !it.failed }.map { it.workspace.bots }
                .distinctUntilChanged().collect { bots ->
                    bookmarks.value = bots
                    profiles.observe(bots.map { it.username })
                    try { store.synchronize(bots.map(LocalAvatarStore::key).toSet()) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Custom-image actions will report failure; remote pictures remain available. */ }
                }
        }
        scope.launch {
            var previous = emptyMap<String, AvatarSource>()
            combine(bookmarks, store.versions, profiles.photos, files.state, account.ready) { bots, custom, remote, downloads, owner ->
                bots.mapNotNull { bot ->
                    val id = LocalAvatarStore.key(bot)
                    val selected = custom[id]?.let { AvatarSource.Local(id, it) } ?: remote[bot.username]?.let { key ->
                        if (owner == null || key.accountId != owner.userId || key.generation != owner.generation) null
                        else downloads[key]?.takeIf { it.stage == TransferStage.READY }?.path?.let { AvatarSource.Remote(key, it) }
                    }
                    selected?.let { id to it }
                }.toMap()
            }.distinctUntilChanged().collectLatest { sources ->
                val reusable = _state.value.images.filter { (key, _) -> sources[key] != null && sources[key] == previous[key] }
                previous = sources
                _state.value = AvatarSnapshot(reusable, sources.filterValues { it is AvatarSource.Local }.keys)
                coroutineScope {
                    sources.forEach { (key, source) -> if (key !in reusable) launch {
                        val bitmap = try {
                            decodePermits.withPermit {
                                when (source) {
                                    is AvatarSource.Local -> store.decode(source.key, source.version)
                                    is AvatarSource.Remote -> withContext(Dispatchers.IO) {
                                        val file = File(source.path).canonicalFile
                                        // Only TDLib's private directory, never a bot-supplied arbitrary path.
                                        if (!files.isCurrent(source.key) || !file.toPath().startsWith(remoteRoot.toPath()) ||
                                            !file.isFile || file.length() > 2L * 1024 * 1024) null
                                        else AvatarDecoder.decode(file, LocalAvatarStore.DISPLAY_SIDE)
                                    }
                                }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                        ensureActive()
                        if (bitmap != null && containsKey(key) && (source !is AvatarSource.Remote || files.isCurrent(source.key))) {
                            _state.update { it.copy(images = it.images + (key to bitmap)) }
                        }
                    } }
                }
            }
        }
    }
    fun contains(bot: SavedBot) = bookmarks.value.any { it.id == bot.id && it.username == bot.username }
    private fun containsKey(key: String) = bookmarks.value.any { LocalAvatarStore.key(it) == key }
    suspend fun import(bot: SavedBot, uri: Uri) {
        require(contains(bot) && uri.scheme == "content")
        store.replace(LocalAvatarStore.key(bot)) { application.contentResolver.openInputStream(uri) ?: error("Image unavailable") }
        check(contains(bot)) { "Bookmark changed during selection" }
    }
    suspend fun reset(bot: SavedBot) {
        require(contains(bot))
        store.remove(LocalAvatarStore.key(bot))
    }
}
