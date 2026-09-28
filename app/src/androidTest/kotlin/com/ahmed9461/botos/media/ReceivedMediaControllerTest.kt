package com.ahmed9461.botos.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahmed9461.botos.model.*
import com.ahmed9461.botos.telegram.runtime.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceivedMediaControllerTest {
    private class FilesAccess : MediaFileAccess {
        override val state = MutableStateFlow<Map<RemoteFileKey, RemoteFileState>>(emptyMap())
        var active = true
        val requested = mutableListOf<RemoteFileKey>()
        override fun key(chat: ChatKey, fileId: Int) = if (active && chat.account == "42:1") RemoteFileKey(42, 1, fileId) else null
        override fun current(key: RemoteFileKey) = active && key.accountId == 42L && key.generation == 1L
        override fun request(key: RemoteFileKey, maximum: Long) { requested.add(key) }
        override fun cancel(key: RemoteFileKey) { state.value += key to RemoteFileState(TransferStage.CANCELLED) }
    }
    private class Fixture {
        val root = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath(), "controller-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val chat = ChatKey("42:1", "100:1")
        val info = MediaInfo(MediaKind.PHOTO, fileId = 7, size = 200)
        val ref = MediaReference(chat, 10, 1, "photo")
        val messages = MutableStateFlow(ConversationState("fixture_bot", ConversationStatus.READY,
            MessageTimeline(chat, listOf(BotMessage(10, chat, 1, listOf(Block.Media("photo", info)))))))
        val files = FilesAccess()
        val controller = ReceivedMediaController(root, messages, files, scope)
        suspend fun stage(stage: MediaStage) = withTimeout(5_000) { controller.state.first { it.items[ref]?.stage == stage } }
        suspend fun close() { withContext(Dispatchers.Main) { controller.leave(); scope.cancel() }; root.deleteRecursively() }
    }
    @Test fun actualImageLoadsWithProgressAndDeletionInvalidatesViewer() = runBlocking<Unit> {
        val f = Fixture()
        try {
            val picture = testPicture(f.root)
            withContext(Dispatchers.Main) {
                f.controller.request(f.ref)
                f.files.state.value = mapOf(RemoteFileKey(42, 1, 7) to RemoteFileState(TransferStage.DOWNLOADING, 50, 100))
            }
            withTimeout(5_000) { f.controller.state.first { it.items[f.ref]?.progress == .5f } }
            withContext(Dispatchers.Main) {
                f.files.state.value = mapOf(RemoteFileKey(42, 1, 7) to RemoteFileState(TransferStage.READY, picture.length(), picture.length(), picture.path))
            }
            assertTrue(f.stage(MediaStage.READY).items[f.ref]?.content is DecodedMedia.Picture)
            withContext(Dispatchers.Main) { f.controller.open(f.ref) }
            assertEquals(f.ref, f.controller.state.value.selected)
            f.messages.value = f.messages.value.copy(timeline = MessageTimeline(f.chat))
            withTimeout(5_000) { f.controller.state.first { it.items.isEmpty() && it.selected == null } }
        } finally { f.close() }
    }
    @Test fun cancelPreventsDelayedReadyFromReopeningOrDecoding() = runBlocking<Unit> {
        val f = Fixture()
        try {
            val picture = testPicture(f.root)
            withContext(Dispatchers.Main) { f.controller.request(f.ref); f.controller.cancel(f.ref) }
            f.files.state.value = mapOf(RemoteFileKey(42, 1, 7) to RemoteFileState(TransferStage.READY, picture.length(), picture.length(), picture.path))
            f.stage(MediaStage.CANCELLED)
            withContext(Dispatchers.Main) { f.controller.open(f.ref) }
            assertNull(f.controller.state.value.selected)
        } finally { f.close() }
    }
    @Test fun foreignRevisionAndVideoAutoloadAreNotRequested() = runBlocking<Unit> {
        val f = Fixture()
        try {
            withContext(Dispatchers.Main) {
                f.controller.request(f.ref.copy(revision = 2))
                f.controller.request(f.ref.copy(chat = f.chat.copy(account = "other")))
                f.messages.value = f.messages.value.copy(timeline = MessageTimeline(f.chat,
                    listOf(BotMessage(10, f.chat, 1, listOf(Block.Media("photo", f.info.copy(kind = MediaKind.VIDEO)))))))
                f.controller.request(f.ref, automatic = true)
            }
            assertTrue(f.files.requested.isEmpty())
            withContext(Dispatchers.Main) { f.controller.request(f.ref) }
            assertEquals(1, f.files.requested.size)
        } finally { f.close() }
    }
    @Test fun logoutDiscardsPreviewAndCannotReadPreviousAccountFile() = runBlocking<Unit> {
        val f = Fixture()
        try {
            withContext(Dispatchers.Main) { f.controller.request(f.ref) }
            f.files.active = false
            f.messages.value = ConversationState()
            withTimeout(5_000) { f.controller.state.first { it.items.isEmpty() } }
            withContext(Dispatchers.Main) { f.controller.request(f.ref); f.controller.open(f.ref) }
            assertEquals(1, f.files.requested.size)
            assertNull(f.controller.state.value.selected)
        } finally { f.close() }
    }
}
