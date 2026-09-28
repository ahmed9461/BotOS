package com.ahmed9461.botos.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class StagedOutgoingMedia(val id: String, val path: String, val bytes: Long) {
    override fun toString() = "StagedOutgoingMedia(bytes=$bytes)"
}

/** App-private inputs remain available after the picker closes and until Telegram confirms a send. */
class OutgoingMediaStore(private val directory: File) {
    private val mutex = Mutex()

    suspend fun stage(fileName: String? = null, open: () -> InputStream): StagedOutgoingMedia = withContext(Dispatchers.IO) {
        mutex.withLock {
            prepare()
            val files = mediaFiles()
            if (files.size >= MAX_FILES) throw IOException("Outgoing attachment count limit")
            val used = files.fold(0L) { sum, file -> sum + file.length() }
            if (used >= MAX_TOTAL_BYTES) throw IOException("Outgoing attachment storage limit")
            val id = UUID.randomUUID().toString().replace("-", "")
            val partial = File(directory, "$id.part")
            val complete = File(directory, fileName?.let { "$id-${safeFileName(it)}" } ?: "$id.media")
            if (partial.exists() || complete.exists()) throw IOException("Attachment identifier collision")
            try {
                var copied = 0L
                open().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) throw IOException("Attachment source did not advance")
                            copied += count
                            if (copied > MAX_FILE_BYTES || copied > MAX_TOTAL_BYTES - used) {
                                throw IOException("Outgoing attachment size limit")
                            }
                            output.write(buffer, 0, count)
                        }
                        if (copied == 0L) throw IOException("Empty attachment")
                        output.fd.sync()
                    }
                }
                if (!partial.renameTo(complete)) throw IOException("Could not retain attachment")
                StagedOutgoingMedia(id, complete.absolutePath, copied)
            } finally {
                partial.delete()
            }
        }
    }

    suspend fun resolve(id: String): StagedOutgoingMedia = withContext(Dispatchers.IO) {
        mutex.withLock {
            validateId(id)
            val file = retainedFile(id) ?: throw IOException("Retained attachment unavailable")
            if (!file.isFile || Files.isSymbolicLink(file.toPath()) ||
                file.canonicalFile.parentFile != directory.canonicalFile ||
                file.length() !in 1..MAX_FILE_BYTES) throw IOException("Retained attachment unavailable")
            StagedOutgoingMedia(id, file.absolutePath, file.length())
        }
    }

    internal suspend fun retainedIds(): Set<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!directory.exists()) return@withLock emptySet()
            val files = mediaFiles()
            val ids = files.map { it.name.take(32) }
            if (ids.size != ids.toSet().size) throw IOException("Ambiguous retained attachment")
            ids.toSet()
        }
    }

    /** Only the journal owner may request deletion after verifying the record state. */
    internal suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            validateId(id)
            val file = retainedFile(id) ?: return@withLock
            if (Files.isSymbolicLink(file.toPath()) || file.canonicalFile.parentFile != directory.canonicalFile) {
                throw IOException("Invalid retained attachment")
            }
            if (file.exists() && !file.delete()) throw IOException("Could not remove attachment")
        }
    }

    private fun prepare() {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Attachment storage unavailable")
        // A .part file is never passed to Telegram or recorded in the journal.
        directory.listFiles().orEmpty().filter { it.name.matches(PART_NAME) }.forEach { file ->
            if (!file.delete()) throw IOException("Incomplete attachment cleanup failed")
        }
    }

    private fun retainedFile(id: String): File? {
        val matches = directory.listFiles().orEmpty().filter {
            it.name == "$id.media" || (it.name.startsWith("$id-") && it.name.matches(MEDIA_NAME))
        }
        if (matches.size > 1) throw IOException("Ambiguous retained attachment")
        return matches.singleOrNull()
    }

    private fun mediaFiles(): List<File> = directory.listFiles().orEmpty().filter { it.name.matches(MEDIA_NAME) }.also { files ->
        if (files.any { !it.isFile || Files.isSymbolicLink(it.toPath()) || it.length() !in 1..MAX_FILE_BYTES }) {
            throw IOException("Invalid retained attachment")
        }
    }

    companion object {
        const val MAX_FILE_BYTES = 50L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 200L * 1024 * 1024
        const val MAX_FILES = 32
        private val ID = Regex("[a-f0-9]{32}")
        private val PART_NAME = Regex("[a-f0-9]{32}\\.part")
        private val MEDIA_NAME = Regex("[a-f0-9]{32}(\\.media|-[\\p{L}\\p{N} ._()\\-]{1,80})")
        internal fun validateId(id: String) { require(ID.matches(id)) }
        private fun safeFileName(value: String): String {
            val name = value.substringAfterLast('/').substringAfterLast('\\')
                .filter { it.isLetterOrDigit() || it in " ._-()" }
                .trim(' ', '.')
                .take(80)
                .trimEnd(' ', '.')
            return name.takeIf { it.isNotBlank() } ?: "attachment.bin"
        }
        fun defaultDirectory(noBackupFilesDir: File) = File(noBackupFilesDir, "telegram/main/files/botos_outgoing")
    }
}
