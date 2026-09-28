package com.ahmed9461.botos.telegram.runtime

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class MediaSessionsTest {
    private class Fake : TdRpc {
        val calls = CopyOnWriteArrayList<JsonObject>()
        val listeners = CopyOnWriteArrayList<(JsonObject) -> Unit>()
        @Volatile var lookupFailures = 0
        @Volatile var lookupFailureCode = 500
        @Volatile var downloadFailures = 0
        @Volatile var photoVisible = true
        var isBot = true
        var completeImmediately = true
        override suspend fun request(command: JsonObject, timeoutMillis: Long): JsonObject {
            calls += command
            if (command.type() == "searchPublicChat" && lookupFailures > 0) {
                lookupFailures--; throw TdFailure(FailureKind.REMOTE, lookupFailureCode)
            }
            if (command.type() == "downloadFile" && downloadFailures > 0) {
                downloadFailures--; throw TdFailure(FailureKind.REMOTE, 500)
            }
            return when (command.type()) {
                "getMe" -> TdJson.command("user") { put("id", 42) }
                "searchPublicChat" -> TdJson.command("chat") { put("id", 100); put("type", TdJson.command("chatTypePrivate") { put("user_id", 9) }) }
                "getUser" -> TdJson.command("user") {
                    put("id", 9); put("type", TdJson.command(if (isBot) "userTypeBot" else "userTypeRegular"))
                    if (photoVisible) put("profile_photo", TdJson.command("profilePhoto") { put("small", file(7, false)) })
                }
                "getFile" -> file(command.number("file_id")!!.toInt(), false)
                "downloadFile" -> file(command.number("file_id")!!.toInt(), completeImmediately)
                else -> TdJson.command("ok")
            }
        }
        fun file(id: Int, ready: Boolean) = TdJson.command("file") {
            put("id", id); put("size", 512); put("local", TdJson.command("localFile") {
                put("is_downloading_completed", ready); put("path", "/private/fixture/$id"); put("downloaded_size", if (ready) 512 else 0)
            })
        }
        fun emit(update: JsonObject) { listeners.forEach { it(update) } }
        override fun observeUpdates(observer: (JsonObject) -> Unit): AutoCloseable {
            listeners += observer; return AutoCloseable { listeners -= observer }
        }
        override suspend fun awaitClosed(timeoutMillis: Long) = Unit
    }
    private class Fixture : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val rpc = Fake()
        val steps = MutableStateFlow(AuthStep.PARAMETERS)
        val account = AccountCoordinator(TelegramAppCredentials(1, "0".repeat(32)), AccountSessionFactory {
            object : AccountSession {
                override val step = steps
                override val rpc = this@Fixture.rpc
                override suspend fun initialize(credentials: TelegramAppCredentials) { steps.value = AuthStep.READY }
                override suspend fun submit(expectedStep: AuthStep, value: String) = Unit
                override suspend fun close() { steps.value = AuthStep.CLOSED }
                override suspend fun logOut() { steps.value = AuthStep.CLOSED }
            }
        }, object : ConnectionPreferences {
            override suspend fun mayRestore() = false
            override suspend fun setMayRestore(value: Boolean) = Unit
        }, scope)
        val files = TelegramFiles(account, scope)
        val profiles = BotProfiles(account, files, scope)
        suspend fun connect() { account.connect(true); withTimeout(5_000) { account.ready.first { it != null } }; yield() }
        override fun close() { scope.cancel() }
    }
    @Test fun signedOutProfilesAndFilesNeverMakeRequests() = runBlocking<Unit> {
        Fixture().use { f ->
            f.profiles.observe(listOf("fixture_bot")); delay(30)
            assertNull(f.files.key(7)); assertTrue(f.rpc.calls.isEmpty())
        }
    }
    @Test fun verifiedProfileUsesTelegramFileAndNeverStartsBot() = runBlocking<Unit> {
        Fixture().use { f ->
            f.profiles.observe(listOf("fixture_bot")); f.connect()
            val photo = withTimeout(5_000) { f.profiles.photos.first { it.isNotEmpty() } }.getValue("fixture_bot")
            withTimeout(5_000) { f.files.state.first { it[photo]?.stage == TransferStage.READY } }
            assertEquals(7, photo.fileId)
            assertFalse(f.rpc.calls.any { it.type() in setOf("sendMessage", "sendBotStartMessage", "openChat") })
            assertEquals(1, f.rpc.calls.count { it.type() == "downloadFile" })
        }
    }
    @Test fun regularUserCannotSupplyABotAvatar() = runBlocking<Unit> {
        Fixture().use { f ->
            f.rpc.isBot = false; f.connect(); f.profiles.observe(listOf("fixture_bot"))
            withTimeout(5_000) { while (f.rpc.calls.none { it.type() == "getUser" }) delay(1) }
            delay(30)
            assertTrue(f.profiles.photos.value.isEmpty())
            assertFalse(f.rpc.calls.any { it.type() == "downloadFile" })
        }
    }
    @Test fun repeatedDownloadsAreCoalescedAndLogoutInvalidatesTheKey() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); val key = f.files.key(7)!!
            repeat(30) { f.files.request(key) }
            withTimeout(5_000) { f.files.state.first { it[key]?.stage == TransferStage.READY } }
            assertEquals(1, f.rpc.calls.count { it.type() == "downloadFile" })
            f.account.logOut()
            withTimeout(5_000) { f.files.state.first { it.isEmpty() } }
            assertFalse(f.files.isCurrent(key))
            val before = f.rpc.calls.size
            f.files.request(key); delay(30); assertEquals(before, f.rpc.calls.size)
        }
    }
    @Test fun oversizedFilesFailBeforeDownloadAndIncompleteDataHasNoPath() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect(); val key = f.files.key(7)!!; f.files.request(key, maxBytes = 256)
            withTimeout(5_000) { f.files.state.first { it[key]?.stage == TransferStage.FAILED } }
            assertNull(f.files.state.value[key]?.path)
            assertFalse(f.rpc.calls.any { it.type() == "downloadFile" })
        }
    }
    @Test fun fileAuthorityRejectsPreviousAccountTimelineAndInvalidFileId() = runBlocking<Unit> {
        Fixture().use { f ->
            f.connect()
            val owner = f.account.ready.value!!
            val chat = com.ahmed9461.botos.model.ChatKey("${owner.userId}:${owner.generation}", "100:1")
            assertEquals(f.files.key(7), f.files.keyForChat(chat, 7))
            assertNull(f.files.keyForChat(chat.copy(account = "other"), 7))
            assertNull(f.files.keyForChat(chat, 0))
            f.account.logOut()
            assertNull(f.files.keyForChat(chat, 7))
        }
    }

    @Test fun transientProfileFailureRetriesWithoutStartingTheBot() = runBlocking<Unit> {
        Fixture().use { f ->
            f.rpc.lookupFailures = 1
            f.profiles.observe(listOf("fixture_bot")); f.connect()
            val key = withTimeout(5_000) { f.profiles.photos.first { it.isNotEmpty() } }.getValue("fixture_bot")
            withTimeout(5_000) { f.files.state.first { it[key]?.stage == TransferStage.READY } }
            assertEquals(2, f.rpc.calls.count { it.type() == "searchPublicChat" })
            assertFalse(f.rpc.calls.any { it.type() in setOf("sendMessage", "sendBotStartMessage", "openChat") })
        }
    }
    @Test fun profileRateLimitIsNotAutomaticallyRetried() = runBlocking<Unit> {
        Fixture().use { f ->
            f.rpc.lookupFailures = 10; f.rpc.lookupFailureCode = 429
            f.profiles.observe(listOf("fixture_bot")); f.connect()
            withTimeout(5_000) { while (f.rpc.calls.none { it.type() == "searchPublicChat" }) delay(1) }
            delay(700)
            assertEquals(1, f.rpc.calls.count { it.type() == "searchPublicChat" })
            assertTrue(f.profiles.photos.value.isEmpty())
        }
    }
    @Test fun explicitAvatarRefreshIsLimitedToSavedNamesAndCoalescesRapidTaps() = runBlocking<Unit> {
        Fixture().use { f ->
            f.rpc.photoVisible = false
            f.profiles.observe(listOf("fixture_bot")); f.connect()
            withTimeout(5_000) { while (f.rpc.calls.none { it.type() == "getUser" }) delay(1) }
            delay(50)
            assertTrue(f.profiles.photos.value.isEmpty())
            f.rpc.photoVisible = true
            f.profiles.refresh("not_saved_bot")
            repeat(30) { f.profiles.refresh("fixture_bot") }
            withTimeout(5_000) { f.profiles.photos.first { it.isNotEmpty() } }
            delay(50)
            assertEquals(2, f.rpc.calls.count { it.type() == "searchPublicChat" })
            assertFalse(f.rpc.calls.any { it.string("username") == "not_saved_bot" })
        }
    }
    @Test fun logoutCancelsPendingAvatarRetryAndClearsPhotos() = runBlocking<Unit> {
        Fixture().use { f ->
            f.rpc.lookupFailures = 10
            f.profiles.observe(listOf("fixture_bot")); f.connect()
            withTimeout(5_000) { while (f.rpc.calls.none { it.type() == "searchPublicChat" }) delay(1) }
            f.account.logOut()
            withTimeout(5_000) { f.account.ready.first { it == null } }
            delay(700)
            assertEquals(1, f.rpc.calls.count { it.type() == "searchPublicChat" })
            assertTrue(f.profiles.photos.value.isEmpty())
        }
    }
    @Test fun failedAvatarTransferRetriesWithinABoundedBudget() = runBlocking<Unit> {
        Fixture().use { f ->
            f.rpc.downloadFailures = 1
            f.profiles.observe(listOf("fixture_bot")); f.connect()
            val key = withTimeout(5_000) { f.profiles.photos.first { it.isNotEmpty() } }.getValue("fixture_bot")
            withTimeout(6_000) { f.files.state.first { it[key]?.stage == TransferStage.READY } }
            assertEquals(2, f.rpc.calls.count { it.type() == "downloadFile" })
        }
    }
}
