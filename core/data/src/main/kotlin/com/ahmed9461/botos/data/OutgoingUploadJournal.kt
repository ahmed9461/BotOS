package com.ahmed9461.botos.data

import android.util.AtomicFile
import com.ahmed9461.botos.telegram.runtime.AttachmentTarget
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class UploadStatus { STAGED, ATTEMPTED, PENDING, UNKNOWN, SUCCEEDED, FAILED }

data class OutgoingUploadRecord(
    val id: String,
    val mediaId: String,
    val accountKey: String,
    val chatId: Long,
    val sendingId: Int,
    val status: UploadStatus,
    val temporaryMessageId: Long = 0,
    val confirmedMessageId: Long = 0,
) {
    override fun toString() = "OutgoingUploadRecord(status=$status)"
}

/** Atomic on-disk boundary before sendMessage. Corrupt data never becomes an empty journal. */
class OutgoingUploadJournal(private val directory: File, private val media: OutgoingMediaStore) {
    private val file = AtomicFile(File(directory, "outgoing-journal.bin"))
    private val mutex = Mutex()

    suspend fun snapshot(): List<OutgoingUploadRecord> = withContext(Dispatchers.IO) {
        mutex.withLock { read() }
    }

    suspend fun reserve(target: AttachmentTarget, staged: StagedOutgoingMedia, sendingId: Int): OutgoingUploadRecord =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                require(sendingId > 0 && target.chatId != 0L && target.chat.account ==
                    "${target.chat.account.substringBefore(':')}:${target.accountGeneration}")
                val userId = target.chat.account.substringBefore(':').toLongOrNull()
                require(userId != null && userId > 0 && target.accountGeneration > 0)
                require(staged == media.resolve(staged.id)) { "Only retained private files may be sent" }
                val records = read()
                if (records.size >= OutgoingMediaStore.MAX_FILES ||
                    records.any { it.sendingId == sendingId || it.mediaId == staged.id }) {
                    throw IOException("Attachment already reserved or journal full")
                }
                val record = OutgoingUploadRecord(UUID.randomUUID().toString(), staged.id,
                    target.chat.account, target.chatId, sendingId, UploadStatus.STAGED)
                write(records + record)
                record
            }
        }

    /** Persist this state before calling TDLib. It must never be entered twice for one record. */
    suspend fun beforeRpc(id: String): OutgoingUploadRecord = mutate(id) { record ->
        require(record.status == UploadStatus.STAGED)
        // A missing or replaced input must fail before persisting an attempted send.
        media.resolve(record.mediaId)
        record.copy(status = UploadStatus.ATTEMPTED)
    }

    suspend fun pending(accountKey: String, chatId: Long, sendingId: Int, temporaryId: Long): OutgoingUploadRecord? =
        terminalOrPending(accountKey, chatId, sendingId) { record ->
            if (temporaryId == 0L || record.status in FINAL) record
            else record.copy(status = UploadStatus.PENDING, temporaryMessageId = temporaryId)
        }

    suspend fun uncertain(id: String): OutgoingUploadRecord = mutate(id) { record ->
        if (record.status in FINAL) record else {
            require(record.status != UploadStatus.STAGED)
            record.copy(status = UploadStatus.UNKNOWN)
        }
    }

    suspend fun succeeded(accountKey: String, chatId: Long, sendingId: Int, temporaryId: Long, messageId: Long): OutgoingUploadRecord? =
        terminalOrPending(accountKey, chatId, sendingId) { record ->
            require(messageId != 0L)
            if (record.status in FINAL) record else record.copy(status = UploadStatus.SUCCEEDED,
                temporaryMessageId = temporaryId, confirmedMessageId = messageId)
        }

    suspend fun failed(accountKey: String, chatId: Long, sendingId: Int, temporaryId: Long): OutgoingUploadRecord? =
        terminalOrPending(accountKey, chatId, sendingId) { record ->
            if (record.status in FINAL) record else record.copy(status = UploadStatus.FAILED,
                temporaryMessageId = temporaryId)
        }

    /** A never-attempted draft may be cancelled; anything that might have reached TDLib remains. */
    suspend fun discardStaged(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val records = read()
            val record = records.singleOrNull { it.id == id } ?: throw IOException("Unknown attachment")
            require(record.status == UploadStatus.STAGED)
            media.remove(record.mediaId)
            write(records - record)
        }
    }

    /** Picker cancellation before reservation must not remove an upload already held by the journal. */
    suspend fun discardPreview(staged: StagedOutgoingMedia) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (read().any { it.mediaId == staged.id }) throw IOException("Attachment is reserved")
            require(staged == media.resolve(staged.id))
            media.remove(staged.id)
        }
    }

    /** Deletion requires a confirmed final success. A failed deletion leaves the record intact. */
    suspend fun releaseSucceeded(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val records = read()
            val record = records.singleOrNull { it.id == id } ?: throw IOException("Unknown attachment")
            require(record.status == UploadStatus.SUCCEEDED)
            media.remove(record.mediaId)
            write(records - record)
        }
    }

    private suspend fun terminalOrPending(accountKey: String, chatId: Long, sendingId: Int,
        change: (OutgoingUploadRecord) -> OutgoingUploadRecord): OutgoingUploadRecord? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val records = read()
            val record = records.singleOrNull { it.accountKey == accountKey && it.chatId == chatId && it.sendingId == sendingId }
                ?: return@withLock null
            if (record.status == UploadStatus.STAGED) return@withLock null
            val next = change(record)
            if (next != record) write(records.map { if (it.id == record.id) next else it })
            next
        }
    }

    private suspend fun mutate(id: String,
        change: suspend (OutgoingUploadRecord) -> OutgoingUploadRecord): OutgoingUploadRecord = withContext(Dispatchers.IO) {
        mutex.withLock {
            val records = read()
            val record = records.singleOrNull { it.id == id } ?: throw IOException("Unknown attachment")
            val next = change(record)
            if (next != record) write(records.map { if (it.id == id) next else it })
            next
        }
    }

    private fun read(): List<OutgoingUploadRecord> {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
        val blob = file.openRead().use { stream ->
            val buffer = ByteArray(4096)
            ByteArrayOutputStream().use { bytes ->
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count == 0 || bytes.size() + count > MAX_JOURNAL_BYTES) throw IOException("Journal exceeds limit")
                    bytes.write(buffer, 0, count)
                }
                bytes.toByteArray()
            }
        }
        if (blob.size < 40) throw IOException("Corrupt attachment journal")
        val content = blob.copyOfRange(0, blob.size - 32)
        val digest = MessageDigest.getInstance("SHA-256").digest(content)
        if (!MessageDigest.isEqual(digest, blob.copyOfRange(content.size, blob.size))) {
            throw IOException("Corrupt attachment journal")
        }
        return DataInputStream(ByteArrayInputStream(content)).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != 1) throw IOException("Unknown journal version")
            val count = input.readInt()
            if (count !in 0..OutgoingMediaStore.MAX_FILES) throw IOException("Invalid attachment count")
            val records = List(count) {
                val id = input.readUTF()
                val mediaId = input.readUTF()
                val account = input.readUTF()
                val chat = input.readLong()
                val sending = input.readInt()
                val status = UploadStatus.entries.getOrNull(input.readUnsignedByte()) ?: throw IOException("Invalid upload status")
                val temporary = input.readLong()
                val confirmed = input.readLong()
                if (!MEDIA_ID.matches(mediaId)) throw IOException("Invalid attachment identifier")
                if (!UUID_PATTERN.matches(id) || !ACCOUNT_PATTERN.matches(account) || chat == 0L || sending <= 0 ||
                    (status == UploadStatus.SUCCEEDED && confirmed == 0L)) throw IOException("Invalid upload record")
                OutgoingUploadRecord(id, mediaId, account, chat, sending, status, temporary, confirmed)
            }
            if (input.read() != -1 || records.map { it.id }.distinct().size != records.size ||
                records.map { it.mediaId }.distinct().size != records.size ||
                records.map { it.accountKey to it.sendingId }.distinct().size != records.size) {
                throw IOException("Duplicate or trailing upload record")
            }
            records
        }
    }

    private fun write(records: List<OutgoingUploadRecord>) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Journal storage unavailable")
        val content = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC); output.writeInt(1); output.writeInt(records.size)
                records.forEach { record ->
                    output.writeUTF(record.id); output.writeUTF(record.mediaId); output.writeUTF(record.accountKey)
                    output.writeLong(record.chatId); output.writeInt(record.sendingId); output.writeByte(record.status.ordinal)
                    output.writeLong(record.temporaryMessageId); output.writeLong(record.confirmedMessageId)
                }
            }
            bytes.toByteArray()
        }
        val blob = content + MessageDigest.getInstance("SHA-256").digest(content)
        if (blob.size > MAX_JOURNAL_BYTES) throw IOException("Journal exceeds limit")
        val output = file.startWrite()
        try { output.write(blob); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }

    companion object {
        private const val MAGIC = 0x424F5554
        private const val MAX_JOURNAL_BYTES = 16 * 1024
        private val FINAL = setOf(UploadStatus.SUCCEEDED, UploadStatus.FAILED)
        private val UUID_PATTERN = Regex("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")
        private val MEDIA_ID = Regex("[a-f0-9]{32}")
        private val ACCOUNT_PATTERN = Regex("[1-9][0-9]*:[1-9][0-9]*")
    }
}
