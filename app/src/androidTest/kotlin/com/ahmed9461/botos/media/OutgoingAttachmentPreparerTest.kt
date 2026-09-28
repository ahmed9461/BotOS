package com.ahmed9461.botos.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahmed9461.botos.data.OutgoingMediaStore
import com.ahmed9461.botos.data.OutgoingUploadJournal
import com.ahmed9461.botos.model.ChatKey
import com.ahmed9461.botos.telegram.runtime.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
class OutgoingAttachmentPreparerTest {
    private val target = AttachmentTarget(ChatKey("42:3", "100:7"), 100, 3, 7, "fixture_bot")
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private class Gateway(val target: AttachmentTarget) : AttachmentGateway {
        override val uploadEvents = MutableSharedFlow<UploadEvent>(extraBufferCapacity = 4)
        override val accountKey = MutableStateFlow<String?>(target.chat.account)
        val sent = CompletableDeferred<PreparedAttachment>()
        override fun captureAttachmentTarget() = target
        override suspend fun sendAttachment(expected: AttachmentTarget, attachment: PreparedAttachment,
            caption: String, sendingId: Int): AttachmentSendResult {
            sent.complete(attachment)
            return AttachmentSendResult.Pending(sendingId, -100)
        }
        override suspend fun inspectAttachment(accountKey: String, chatId: Long,
            temporaryMessageId: Long, sendingId: Int) = null
    }

    private inner class Fixture : AutoCloseable {
        val dir = File(context.cacheDir, "preparer-${UUID.randomUUID()}")
        val media = OutgoingMediaStore(dir)
        val journal = OutgoingUploadJournal(dir, media)
        val gateway = Gateway(target)
        val job = SupervisorJob()
        val uploads = TelegramUploads(gateway, media, journal, CoroutineScope(job + Dispatchers.Default))
        val preparer = OutgoingAttachmentPreparer(context, uploads)
        override fun close() { job.cancel(); dir.deleteRecursively() }
    }

    @Test fun selectedPhotoIsReencodedBeforeOneDurableSendAndReleasedOnFinalUpdate() = runBlocking<Unit> {
        Fixture().use { f ->
            val bitmap = Bitmap.createBitmap(3200, 800, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xff65518f.toInt())
            val raw = ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); output.toByteArray()
            }
            bitmap.recycle()
            val preview = f.preparer.prepare(target, "selected.png", AttachmentKind.PHOTO) { ByteArrayInputStream(raw) }
            assertEquals(AttachmentKind.PHOTO, preview.attachment.kind)
            assertTrue(preview.staged.path.endsWith("-photo.jpg"))
            assertEquals(1600, preview.attachment.width)
            assertEquals(400, preview.attachment.height)
            assertNotNull(preview.image)
            assertTrue(preview.staged.bytes in 1..10L * 1024 * 1024)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(preview.staged.path, bounds)
            assertEquals(1600, bounds.outWidth)
            assertEquals(400, bounds.outHeight)
            assertTrue(File(preview.staged.path).inputStream().use { it.read() == 0xff && it.read() == 0xd8 })
            assertEquals(1, f.dir.listFiles()!!.count { it.name.endsWith("-photo.jpg") })
            assertTrue(f.uploads.queue(target, preview.staged, preview.attachment, "صورة") is UploadQueueResult.Queued)
            assertEquals(AttachmentKind.PHOTO, withTimeout(5_000) { f.gateway.sent.await() }.kind)
            val record = f.journal.snapshot().single()
            withTimeout(5_000) { f.uploads.state.first { it.records.singleOrNull()?.temporaryMessageId == -100L } }
            f.gateway.uploadEvents.emit(UploadEvent.Succeeded("42:3", 100, record.sendingId, -100, 110))
            withTimeout(5_000) { f.uploads.state.first { it.records.isEmpty() } }
            assertFalse(File(preview.staged.path).exists())
        }
    }

    @Test fun mp4H264IsVideoAndWebmRequiresExplicitFilePreview() = runBlocking<Unit> {
        Fixture().use { f ->
            val mp4 = f.preparer.prepare(target, "clip.mp4", AttachmentKind.VIDEO) {
                InstrumentationRegistry.getInstrumentation().context.assets.open("synthetic-video.mp4")
            }
            assertEquals(AttachmentKind.VIDEO, mp4.attachment.kind)
            assertTrue(mp4.attachment.width > 0 && mp4.attachment.height > 0)
            assertNotNull(mp4.image)
            assertFalse(mp4.fileFallback)
            val webm = f.preparer.prepare(target, "clip.webm", AttachmentKind.VIDEO) {
                InstrumentationRegistry.getInstrumentation().context.assets.open("synthetic-sticker.webm")
            }
            assertEquals(AttachmentKind.DOCUMENT, webm.attachment.kind)
            assertTrue(webm.fileFallback)
            f.uploads.discardPreview(mp4.staged)
            f.uploads.discardPreview(webm.staged)
            assertTrue(f.dir.listFiles().orEmpty().none { it.name.endsWith(".mp4") || it.name.endsWith(".webm") })
        }
    }

    @Test fun invalidPhotoCannotLeaveAPreviewOrJournalRecord() = runBlocking<Unit> {
        Fixture().use { f ->
            assertThrows(IOException::class.java) { runBlocking {
                f.preparer.prepare(target, "bad.jpg", AttachmentKind.PHOTO) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
            } }
            assertTrue(f.journal.snapshot().isEmpty())
            assertTrue(f.dir.listFiles().orEmpty().none { it.name.endsWith(".media") || it.name.endsWith("-bad.jpg") })
        }
    }

    @Test fun knownM4aIsAudioWithDurationAndUnknownAudioIsExplicitlyAFile() = runBlocking<Unit> {
        Fixture().use { f ->
            val audio = f.preparer.prepare(target, "tone.m4a", AttachmentKind.AUDIO) {
                InstrumentationRegistry.getInstrumentation().context.assets.open("synthetic-audio.m4a")
            }
            assertEquals(AttachmentKind.AUDIO, audio.attachment.kind)
            assertTrue(audio.attachment.duration > 0)
            assertEquals("tone.m4a", audio.attachment.fileName)
            val other = f.preparer.prepare(target, "clip.webm", AttachmentKind.AUDIO) {
                InstrumentationRegistry.getInstrumentation().context.assets.open("synthetic-sticker.webm")
            }
            assertEquals(AttachmentKind.DOCUMENT, other.attachment.kind)
            assertTrue(other.fileFallback)
            f.uploads.discardPreview(audio.staged)
            f.uploads.discardPreview(other.staged)
        }
    }
}
