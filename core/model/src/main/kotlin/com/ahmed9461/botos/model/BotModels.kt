package com.ahmed9461.botos.model

import java.net.URI
import java.util.Base64
import java.util.Locale

/** Local bookmark, not proof that a username resolves to a Telegram bot. */
data class SavedBot(val id: String, val username: String, val title: String)
enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class Preferences(val theme: ThemeMode = ThemeMode.SYSTEM, val reduceMotion: Boolean = false)
data class Workspace(val bots: List<SavedBot> = emptyList(), val preferences: Preferences = Preferences())

object BotNames {
    private val usernamePattern = Regex("[A-Za-z][A-Za-z0-9_]{3,31}")
    fun normalize(input: String): String? {
        var value = input.trim()
        if (value.startsWith("https://", ignoreCase = true)) {
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            if (uri.host?.lowercase(Locale.ROOT) !in setOf("t.me", "telegram.me") ||
                uri.rawQuery != null || uri.rawFragment != null || uri.userInfo != null || uri.port != -1) return null
            value = uri.path.removePrefix("/").removeSuffix("/")
        }
        value = value.removePrefix("@")
        return value.takeIf { usernamePattern.matches(it) }?.lowercase(Locale.ROOT)
    }
    fun validTitle(value: String) = value.trim().length in 1..40 && value.none { it.isISOControl() }
    fun isDuplicate(bots: List<SavedBot>, username: String, excludingId: String? = null) =
        bots.any { it.id != excludingId && it.username.equals(username, ignoreCase = true) }
}

/** Bounded, versioned format for local bookmarks only. Never stores message/session content. */
object WorkspaceCodec {
    const val MAX_BOTS = 100
    private fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decode(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    fun encodeBots(bots: List<SavedBot>): String {
        validate(bots)
        return "1\n" + bots.joinToString("\n") { "${encode(it.id)}|${it.username}|${encode(it.title)}" }
    }
    fun decodeBots(value: String): List<SavedBot> {
        if (value.isEmpty()) return emptyList()
        require(value.length <= 100_000) { "Invalid workspace size" }
        val lines = value.split('\n')
        require(lines.first() == "1") { "Unsupported workspace version" }
        val bots = lines.drop(1).filter(String::isNotEmpty).map {
            val parts = it.split('|')
            require(parts.size == 3) { "Invalid bookmark" }
            SavedBot(decode(parts[0]), parts[1], decode(parts[2]))
        }
        validate(bots)
        return bots
    }
    private fun validate(bots: List<SavedBot>) {
        require(bots.size <= MAX_BOTS)
        require(bots.map { it.id }.distinct().size == bots.size)
        require(bots.map { it.username.lowercase(Locale.ROOT) }.distinct().size == bots.size)
        require(bots.all { it.id.isNotBlank() && it.id.length <= 64 &&
            BotNames.normalize(it.username) == it.username && BotNames.validTitle(it.title) })
    }
}
