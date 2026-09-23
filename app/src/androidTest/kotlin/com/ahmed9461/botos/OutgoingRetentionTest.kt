package com.ahmed9461.botos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahmed9461.botos.data.OutgoingMediaStore
import com.ahmed9461.botos.data.OutgoingUploadJournal
import com.ahmed9461.botos.data.UploadStatus
import com.ahmed9461.botos.model.ChatKey
import com.ahmed9461.botos.telegram.runtime.AttachmentTarget
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutgoingRetentionTest {
    private val target = AttachmentTarget(ChatKey("42:3", "100:7"), 100, 3, 7, "alpha_bot")

    private fun root() = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "outgoing-test-${UUID.randomUUID()}")

    @Test fun stagedInputSurvivesReopenAndCancelledPreviewIsRemoved() = runBlocking<Unit> {
        val dir = root()
        try {
            val initial = OutgoingMediaStore(dir).stage { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
            assertEquals(3, OutgoingMediaStore(dir).resolve(initial.id).bytes)
            assertArrayEquals(byteArrayOf(1, 2, 3), File(initial.path).readBytes())
            OutgoingUploadJournal(dir, OutgoingMediaStore(dir)).discardPreview(initial)
            assertFalse(File(initial.path).exists())
            assertTrue(OutgoingUploadJournal(dir, OutgoingMediaStore(dir)).snapshot().isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun journalIsDurableBeforeRpcAndUnknownCannotBeDeletedOrSentTwice() = runBlocking<Unit> {
        val dir = root()
        try {
            val media = OutgoingMediaStore(dir)
            val file = media.stage { ByteArrayInputStream(byteArrayOf(4, 5, 6)) }
            val journal = OutgoingUploadJournal(dir, media)
            val reserved = journal.reserve(target, file, 901)
            assertEquals(UploadStatus.STAGED, OutgoingUploadJournal(dir, media).snapshot().single().status)
            assertEquals(UploadStatus.ATTEMPTED, journal.beforeRpc(reserved.id).status)
            assertEquals(UploadStatus.UNKNOWN, journal.uncertain(reserved.id).status)
            val reopened = OutgoingUploadJournal(dir, OutgoingMediaStore(dir))
            assertEquals(UploadStatus.UNKNOWN, reopened.snapshot().single().status)
            assertTrue(File(file.path).exists())
            assertThrows(IllegalArgumentException::class.java) { runBlocking { reopened.releaseSucceeded(reserved.id) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { reopened.beforeRpc(reserved.id) } }
            assertThrows(IOException::class.java) { runBlocking { reopened.discardPreview(file) } }
        } finally { dir.deleteRecursively() }
    }

    @Test fun terminalBeforeLatePendingNeverRegressesAndOnlySuccessReleasesFile() = runBlocking<Unit> {
        val dir = root()
        try {
            val media = OutgoingMediaStore(dir)
            val staged = media.stage { ByteArrayInputStream(byteArrayOf(9)) }
            val journal = OutgoingUploadJournal(dir, media)
            val record = journal.reserve(target, staged, 902)
            journal.beforeRpc(record.id)
            assertNull(journal.succeeded("43:3", 100, 902, -99, 104))
            assertNull(journal.succeeded("42:3", 200, 902, -99, 104))
            assertEquals(UploadStatus.ATTEMPTED, journal.snapshot().single().status)
            journal.succeeded("42:3", 100, 902, -99, 104)
            journal.pending("42:3", 100, 902, -99)
            assertEquals(UploadStatus.SUCCEEDED, OutgoingUploadJournal(dir, media).snapshot().single().status)
            journal.releaseSucceeded(record.id)
            assertFalse(File(staged.path).exists())
            assertTrue(journal.snapshot().isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedFinalStateRetainsFileAndCannotBeTreatedAsSuccess() = runBlocking<Unit> {
        val dir = root()
        try {
            val media = OutgoingMediaStore(dir)
            val staged = media.stage { ByteArrayInputStream(byteArrayOf(1)) }
            val journal = OutgoingUploadJournal(dir, media)
            val record = journal.reserve(target, staged, 903)
            journal.beforeRpc(record.id)
            journal.failed("42:3", 100, 903, -90)
            journal.succeeded("42:3", 100, 903, -90, 120)
            assertEquals(UploadStatus.FAILED, journal.snapshot().single().status)
            assertTrue(File(staged.path).exists())
            assertThrows(IllegalArgumentException::class.java) { runBlocking { journal.releaseSucceeded(record.id) } }
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptionFailsClosedWithoutDiscardingRetainedInput() = runBlocking<Unit> {
        val dir = root()
        try {
            val media = OutgoingMediaStore(dir)
            val staged = media.stage { ByteArrayInputStream(byteArrayOf(7)) }
            val journal = OutgoingUploadJournal(dir, media)
            journal.reserve(target, staged, 904)
            val disk = File(dir, "outgoing-journal.bin")
            disk.outputStream().use { it.write(byteArrayOf(1, 2, 3)) }
            assertThrows(IOException::class.java) { runBlocking { journal.snapshot() } }
            assertTrue(File(staged.path).exists())
            assertThrows(IOException::class.java) { runBlocking { journal.discardPreview(staged) } }
        } finally { dir.deleteRecursively() }
    }

    @Test fun fileCountLimitDoesNotEvictUncertainOrPendingMedia() = runBlocking<Unit> {
        val dir = root()
        try {
            val media = OutgoingMediaStore(dir)
            repeat(OutgoingMediaStore.MAX_FILES) { media.stage { ByteArrayInputStream(byteArrayOf(1)) } }
            assertThrows(IOException::class.java) {
                runBlocking { media.stage { ByteArrayInputStream(byteArrayOf(2)) } }
            }
            assertEquals(OutgoingMediaStore.MAX_FILES, dir.listFiles()!!.count { it.extension == "media" })
        } finally { dir.deleteRecursively() }
    }

    @Test fun missingOrForeignFileCannotReachAttemptedState() = runBlocking<Unit> {
        val dir = root()
        try {
            val media = OutgoingMediaStore(dir)
            val staged = media.stage { ByteArrayInputStream(byteArrayOf(1)) }
            val journal = OutgoingUploadJournal(dir, media)
            val outside = staged.copy(path = File(dir.parentFile, "foreign.media").absolutePath)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { journal.reserve(target, outside, 905) } }
            val record = journal.reserve(target, staged, 905)
            File(staged.path).delete()
            assertThrows(IOException::class.java) { runBlocking { journal.beforeRpc(record.id) } }
            assertEquals(UploadStatus.STAGED, journal.snapshot().single().status)
        } finally { dir.deleteRecursively() }
    }
}
