package com.ahmed9461.botos.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ahmed9461.botos.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.workspaceDataStore by preferencesDataStore(name = "botos_workspace")
data class StoreSnapshot(val workspace: Workspace = Workspace(), val loading: Boolean = false, val failed: Boolean = false)
class InvalidBookmark : IllegalArgumentException()
class DuplicateBookmark : IllegalArgumentException()
class BookmarkLimit : IllegalStateException()

class WorkspaceStore(context: Context) {
    private val store = context.applicationContext.workspaceDataStore
    private val botsKey = stringPreferencesKey("bots_v1")
    private val themeKey = stringPreferencesKey("theme")
    private val motionKey = booleanPreferencesKey("reduce_motion")
    val state: Flow<StoreSnapshot> = store.data.map { StoreSnapshot(read(it)) }
        .catch { emit(StoreSnapshot(failed = true)) }

    private fun read(data: androidx.datastore.preferences.core.Preferences): Workspace = Workspace(
        bots = WorkspaceCodec.decodeBots(data[botsKey].orEmpty()),
        preferences = com.ahmed9461.botos.model.Preferences(
            theme = ThemeMode.entries.firstOrNull { it.name == data[themeKey] } ?: ThemeMode.SYSTEM,
            reduceMotion = data[motionKey] ?: false,
        ),
    )
    suspend fun saveBot(id: String?, input: String, title: String): String {
        val username = BotNames.normalize(input) ?: throw InvalidBookmark()
        if (!BotNames.validTitle(title)) throw InvalidBookmark()
        val finalId = id ?: UUID.randomUUID().toString()
        store.edit { data ->
            val bots = read(data).bots
            if (BotNames.isDuplicate(bots, username, id)) throw DuplicateBookmark()
            if (id == null && bots.size >= WorkspaceCodec.MAX_BOTS) throw BookmarkLimit()
            if (id != null && bots.none { it.id == id }) throw InvalidBookmark()
            val replacement = SavedBot(finalId, username, title.trim())
            val next = if (id == null) bots + replacement else bots.map { if (it.id == id) replacement else it }
            data[botsKey] = WorkspaceCodec.encodeBots(next)
        }
        return finalId
    }
    suspend fun deleteBot(id: String) { store.edit { it[botsKey] = WorkspaceCodec.encodeBots(read(it).bots.filterNot { bot -> bot.id == id }) } }
    suspend fun moveBot(id: String, delta: Int) {
        require(delta == -1 || delta == 1)
        store.edit { data ->
            val bots = read(data).bots.toMutableList()
            val index = bots.indexOfFirst { it.id == id }
            val target = index + delta
            if (index >= 0 && target in bots.indices) {
                val bot = bots.removeAt(index)
                bots.add(target, bot)
                data[botsKey] = WorkspaceCodec.encodeBots(bots)
            }
        }
    }
    suspend fun setTheme(theme: ThemeMode) { store.edit { read(it); it[themeKey] = theme.name } }
    suspend fun setReducedMotion(reduce: Boolean) { store.edit { read(it); it[motionKey] = reduce } }
}
