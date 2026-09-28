package com.ahmed9461.botos.media

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahmed9461.botos.data.OutgoingMediaStore
import com.ahmed9461.botos.data.OutgoingUploadJournal
import com.ahmed9461.botos.model.ChatKey
import com.ahmed9461.botos.telegram.runtime.*
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutgoingVoiceSessionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val target = AttachmentTarget(ChatKey("42:3", "100:7"), 100, 3, 7, "fixture_bot")

    private class Gateway(var selected: AttachmentTarget) : AttachmentGateway {
        override val uploadEvents = MutableSharedFlow<UploadEvent>(extraBufferCapacity = 4)
        override val accountKey = MutableStateFlow<String?>(selected.chat.account)
        val sent = CompletableDeferred<PreparedAttachment>()
        var sends = 0
        override fun captureAttachmentTarget() = selected
        override suspend fun sendAttachment(expected: AttachmentTarget, attachment: PreparedAttachment,
            caption: String, sendingId: Int): AttachmentSendResult {
            sends++
            sent.complete(attachment)
            return AttachmentSendResult.Pending(sendingId, -100)
        }
        override suspend fun inspectAttachment(accountKey: String, chatId: Long,
            temporaryMessageId: Long, sendingId: Int) = null
    }

    private class Fixture(val context: android.content.Context, val target: AttachmentTarget) : AutoCloseable {
        val dir = File(context.cacheDir, "voice-${UUID.randomUUID()}").apply { mkdirs() }
        val media = OutgoingMediaStore(dir)
        val journal = OutgoingUploadJournal(dir, media)
        val gateway = Gateway(target)
        val job = SupervisorJob()
        val uploads = TelegramUploads(gateway, media, journal, CoroutineScope(job + Dispatchers.Default))
        override fun close() { job.cancel(); dir.deleteRecursively() }
    }

    private class FixtureCapture(private val recorded: File) : VoiceCapture {
        var aborted = 0
        override suspend fun start() = Unit
        override suspend fun finish() = RecordedVoice(recorded, 2)
        override fun abort() { aborted++ }
    }

    private fun Fixture.clip(): File = File(dir, "raw-voice.m4a").also { file ->
        instrumentation.context.assets.open("synthetic-audio.m4a").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
    }

    @Test fun permissionDenialAndBotChangeNeverStartCapture() {
        var current: AttachmentTarget? = target
        val gate = VoicePermissionGate { current }
        assertEquals(target, gate.request())
        assertNull(gate.request())
        assertEquals(VoicePermissionResult.DENIED, gate.resolve(false))
        assertEquals(VoicePermissionResult.IGNORED, gate.resolve(true))
        assertEquals(target, gate.request())
        current = target.copy(viewGeneration = 8)
        assertEquals(VoicePermissionResult.CHANGED, gate.resolve(true))
        current = target
        assertEquals(target, gate.request())
        gate.clear() // The activity left the foreground while the system permission sheet was open.
        assertEquals(VoicePermissionResult.IGNORED, gate.resolve(true))
        assertEquals(target, gate.request())
        assertEquals(VoicePermissionResult.GRANTED, gate.resolve(true))
    }

    @Test fun recordedM4aIsPreviewedThenOneDurableVoiceSendAndFinalRelease() = runBlocking<Unit> {
        Fixture(context, target).use { f ->
            val raw = f.clip()
            val capture = FixtureCapture(raw)
            val preview = OutgoingVoiceSession(capture, f.uploads).complete(target)
            assertEquals(AttachmentKind.VOICE, preview.attachment.kind)
            assertTrue(preview.staged.path.endsWith("-voice.m4a"))
            assertEquals(2, preview.attachment.duration)
            assertFalse(raw.exists())
            assertEquals(1, capture.aborted)
            assertTrue(f.journal.snapshot().isEmpty()) // Stopping the mic cannot send a message.
            assertTrue(f.uploads.queue(target, preview.staged, preview.attachment, "") is UploadQueueResult.Queued)
            assertEquals(AttachmentKind.VOICE, withTimeout(5_000) { f.gateway.sent.await() }.kind)
            withTimeout(5_000) { f.uploads.state.first { it.records.singleOrNull()?.temporaryMessageId == -100L } }
            val record = f.journal.snapshot().single()
            f.gateway.uploadEvents.emit(UploadEvent.Succeeded(target.chat.account, target.chatId,
                record.sendingId, -100, 120))
            withTimeout(5_000) { f.uploads.state.first { it.records.isEmpty() } }
            assertFalse(File(preview.staged.path).exists())
            assertEquals(1, f.gateway.sends)
        }
    }

    @Test fun botSwitchAfterRecordingDropsRawClipWithoutStagingOrSending() = runBlocking<Unit> {
        Fixture(context, target).use { f ->
            val raw = f.clip()
            val capture = FixtureCapture(raw)
            f.gateway.selected = target.copy(chatId = 200)
            assertThrows(VoiceTargetChanged::class.java) { runBlocking {
                OutgoingVoiceSession(capture, f.uploads).complete(target)
            } }
            assertFalse(raw.exists())
            assertEquals(1, capture.aborted)
            assertTrue(f.journal.snapshot().isEmpty())
            assertEquals(0, f.gateway.sends)
            assertTrue(f.dir.listFiles().orEmpty().none { it.name.endsWith("-voice.m4a") })
        }
    }

    @Test fun failedStopOrExplicitAbortCannotLeaveAQueuedVoice() = runBlocking<Unit> {
        Fixture(context, target).use { f ->
            val fake = object : VoiceCapture {
                var aborted = 0
                override suspend fun start() = Unit
                override suspend fun finish(): RecordedVoice = throw IOException("Invalid short capture")
                override fun abort() { aborted++ }
            }
            assertThrows(IOException::class.java) { runBlocking {
                OutgoingVoiceSession(fake, f.uploads).complete(target)
            } }
            assertEquals(1, fake.aborted)
            assertTrue(f.journal.snapshot().isEmpty())
            assertEquals(0, f.gateway.sends)
        }
    }

    @Test fun emulatorMicrophoneRecordsValidatedAacMonoAndAbortRemovesRawFile() = runBlocking<Unit> {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        }
        val before = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("botos-voice-") }.map { it.name }.toSet()
        val capture = AndroidVoiceCapture(context)
        try {
            capture.start()
            delay(1_800) // The encoder needs enough actual samples for a valid M4A container.
            val recorded = capture.finish()
            try {
                assertTrue(recorded.durationSeconds in 1..60)
                assertTrue(recorded.file.length() in 1..10L * 1024 * 1024)
            } finally { recorded.file.delete() }
            capture.start()
        } finally { capture.abort() }
        val after = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("botos-voice-") }.map { it.name }.toSet()
        assertEquals(before, after)
    }
}
