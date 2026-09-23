package com.ahmed9461.botos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahmed9461.botos.data.OutgoingMediaStore
import com.ahmed9461.botos.data.OutgoingUploadJournal
import com.ahmed9461.botos.data.UploadStatus
import com.ahmed9461.botos.media.TelegramUploads
import com.ahmed9461.botos.media.UploadQueueResult
import com.ahmed9461.botos.model.ChatKey
import com.ahmed9461.botos.telegram.runtime.*
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramUploadsTest {
    private val target = AttachmentTarget(ChatKey("42:3", "100:7"), 100, 3, 7, "alpha_bot")

    private class Gateway(var selected: AttachmentTarget) : AttachmentGateway {
        val events = MutableSharedFlow<UploadEvent>(extraBufferCapacity = 16)
        override val uploadEvents = events
        override val accountKey = MutableStateFlow<String?>(selected.chat.account)
        val reachedRpc = CompletableDeferred<Unit>()
        val response = CompletableDeferred<AttachmentSendResult>()
        var sends = 0
        var probes = 0
        var probeResult: UploadEvent? = null
        override fun captureAttachmentTarget() = selected
        override suspend fun sendAttachment(expected: AttachmentTarget, attachment: PreparedAttachment,
            caption: String, sendingId: Int): AttachmentSendResult {
            sends++
            reachedRpc.complete(Unit)
            return response.await()
        }
        override suspend fun inspectAttachment(accountKey: String, chatId: Long,
            temporaryMessageId: Long, sendingId: Int): UploadEvent? {
            probes++
            return probeResult
        }
    }

    private class Fixture(target: AttachmentTarget) : AutoCloseable {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "upload-test-${UUID.randomUUID()}")
        val media = OutgoingMediaStore(root)
        val journal = OutgoingUploadJournal(root, media)
        val gateway = Gateway(target)
        private val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val uploads = TelegramUploads(gateway, media, journal, scope)
        suspend fun staged() = uploads.stage { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        override fun close() { job.cancel(); root.deleteRecursively() }
    }

    @Test fun queuePersistsAttemptBeforeRpcAndFinalUpdateReleasesFile() = runBlocking<Unit> {
        Fixture(target).use { f ->
            val staged = f.staged()
            val result = f.uploads.queue(target, staged,
                PreparedAttachment(staged.path, AttachmentKind.PHOTO, staged.bytes, 10, 10), "")
            assertTrue(result is UploadQueueResult.Queued)
            withTimeout(5_000) { f.gateway.reachedRpc.await() }
            val record = f.journal.snapshot().single()
            assertEquals(UploadStatus.ATTEMPTED, record.status)
            assertTrue(File(staged.path).exists())
            f.gateway.response.complete(AttachmentSendResult.Pending(record.sendingId, -10))
            withTimeout(5_000) { f.uploads.state.first { it.records.singleOrNull()?.status == UploadStatus.PENDING } }
            f.gateway.events.emit(UploadEvent.Succeeded("42:3", 100, record.sendingId, -10, 80))
            withTimeout(5_000) { f.uploads.state.first { it.records.isEmpty() } }
            assertFalse(File(staged.path).exists())
            assertEquals(1, f.gateway.sends)
        }
    }

    @Test fun sameStagedFileCannotBeQueuedTwiceOrRetriedOnUncertainResponse() = runBlocking<Unit> {
        Fixture(target).use { f ->
            val staged = f.staged()
            val attachment = PreparedAttachment(staged.path, AttachmentKind.DOCUMENT, staged.bytes)
            assertTrue(f.uploads.queue(target, staged, attachment, "") is UploadQueueResult.Queued)
            withTimeout(5_000) { f.gateway.reachedRpc.await() }
            assertEquals(UploadQueueResult.Unavailable, f.uploads.queue(target, staged, attachment, ""))
            f.gateway.response.complete(AttachmentSendResult.Uncertain(f.journal.snapshot().single().sendingId))
            withTimeout(5_000) { f.uploads.state.first { it.records.singleOrNull()?.status == UploadStatus.UNKNOWN } }
            assertEquals(1, f.gateway.sends)
            assertTrue(File(staged.path).exists())
        }
    }

    @Test fun finalUpdateBeforeRpcResponseCannotRestorePendingOrResend() = runBlocking<Unit> {
        Fixture(target).use { f ->
            val staged = f.staged()
            assertTrue(f.uploads.queue(target, staged,
                PreparedAttachment(staged.path, AttachmentKind.PHOTO, staged.bytes, 10, 10), "") is UploadQueueResult.Queued)
            withTimeout(5_000) { f.gateway.reachedRpc.await() }
            val record = f.journal.snapshot().single()
            f.gateway.events.emit(UploadEvent.Succeeded("42:3", 100, record.sendingId, -50, 150))
            withTimeout(5_000) { f.uploads.state.first { it.records.isEmpty() } }
            f.gateway.response.complete(AttachmentSendResult.Pending(record.sendingId, -50))
            delay(100)
            assertTrue(f.journal.snapshot().isEmpty())
            assertFalse(File(staged.path).exists())
            assertEquals(1, f.gateway.sends)
        }
    }

    @Test fun interruptedRpcRetainsUncertainFileForExplicitReview() = runBlocking<Unit> {
        Fixture(target).use { f ->
            val staged = f.staged()
            assertTrue(f.uploads.queue(target, staged,
                PreparedAttachment(staged.path, AttachmentKind.DOCUMENT, staged.bytes), "") is UploadQueueResult.Queued)
            withTimeout(5_000) { f.gateway.reachedRpc.await() }
            f.gateway.response.completeExceptionally(CancellationException("Synthetic cancellation"))
            withTimeout(5_000) { f.uploads.state.first { it.records.singleOrNull()?.status == UploadStatus.UNKNOWN } }
            assertTrue(File(staged.path).exists())
            assertEquals(1, f.gateway.sends)
        }
    }

    @Test fun staleTargetAndWrongAccountOrChatUpdateCannotAffectAnotherUpload() = runBlocking<Unit> {
        Fixture(target).use { f ->
            val staged = f.staged()
            val attachment = PreparedAttachment(staged.path, AttachmentKind.AUDIO, staged.bytes)
            f.gateway.selected = target.copy(chatId = 200)
            assertEquals(UploadQueueResult.TargetChanged, f.uploads.queue(target, staged, attachment, ""))
            assertTrue(f.journal.snapshot().isEmpty())
            f.gateway.selected = target
            assertTrue(f.uploads.queue(target, staged, attachment, "") is UploadQueueResult.Queued)
            withTimeout(5_000) { f.gateway.reachedRpc.await() }
            val record = f.journal.snapshot().single()
            f.gateway.response.complete(AttachmentSendResult.Pending(record.sendingId, -11))
            withTimeout(5_000) { f.uploads.state.first { it.records.singleOrNull()?.status == UploadStatus.PENDING } }
            f.gateway.events.emit(UploadEvent.Succeeded("43:4", 100, record.sendingId, -11, 81))
            f.gateway.events.emit(UploadEvent.Succeeded("42:3", 200, record.sendingId, -11, 81))
            delay(100)
            assertEquals(UploadStatus.PENDING, f.journal.snapshot().single().status)
            assertTrue(File(staged.path).exists())
        }
    }

    @Test fun restartProbesKnownTemporaryIdForSameUserWithoutResending() = runBlocking<Unit> {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "upload-restart-${UUID.randomUUID()}")
        val job = SupervisorJob()
        try {
            val media = OutgoingMediaStore(root)
            val journal = OutgoingUploadJournal(root, media)
            val staged = media.stage { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
            val old = target.copy(chat = ChatKey("42:2", "100:6"), accountGeneration = 2, viewGeneration = 6)
            val record = journal.reserve(old, staged, 912)
            journal.beforeRpc(record.id)
            journal.pending("42:2", 100, 912, -12)
            val gateway = Gateway(target)
            gateway.accountKey.value = "42:4"
            gateway.probeResult = UploadEvent.Failed("42:4", 100, 912, -12)
            val resumed = TelegramUploads(gateway, media, OutgoingUploadJournal(root, media),
                CoroutineScope(job + Dispatchers.Default))
            withTimeout(5_000) { resumed.state.first { it.records.singleOrNull()?.status == UploadStatus.FAILED } }
            assertEquals(1, gateway.probes)
            assertEquals(0, gateway.sends)
            assertTrue(File(staged.path).exists())
        } finally { job.cancel(); root.deleteRecursively() }
    }
}
