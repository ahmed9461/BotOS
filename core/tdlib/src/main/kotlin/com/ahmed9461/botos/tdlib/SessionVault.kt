package com.ahmed9461.botos.tdlib

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** No plaintext key in preferences, backups or files. Failure never silently replaces a key. */
internal class SessionVault(context: Context, slot: String = "main") {
    init { require(Regex("[a-zA-Z0-9_-]{1,64}").matches(slot)) }
    val directory = File(context.noBackupFilesDir, "telegram/$slot")
    val databaseDirectory = File(directory, "database")
    val filesDirectory = File(directory, "files")
    internal val alias = "botos.telegram.wrap.$slot.v1"
    private val wrapped = AtomicFile(File(directory, "database-key.bin"))
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    fun loadOrCreate(): ByteArray = try {
        val store = keyStore()
        val exists = wrapped.baseFile.exists() || File(wrapped.baseFile.path + ".bak").exists()
        if (exists) {
            val key = store.getKey(alias, null) as? SecretKey ?: error("Missing wrapping key")
            val encoded = wrapped.openRead().use { stream ->
                val bytes = ByteArray(68)
                DataInputStream(stream).readFully(bytes)
                require(stream.read() == -1)
                bytes
            }
            val buffer = ByteBuffer.wrap(encoded)
            require(buffer.int == 0x42544B31 && buffer.int == 12)
            val iv = ByteArray(12).also { buffer.get(it) }
            val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).also {
                require(it.size == 32)
                check(databaseDirectory.mkdirs() || databaseDirectory.isDirectory)
                check(filesDirectory.mkdirs() || filesDirectory.isDirectory)
            }
        } else {
            // Existing database/files without their encryption key must never be treated as new.
            require(!directory.exists() || directory.walkTopDown().none { it.isFile })
            check(directory.mkdirs() || directory.isDirectory)
            val key = store.getKey(alias, null) as? SecretKey ?: createWrappingKey()
            val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val encrypted = cipher.doFinal(raw)
                val blob = ByteBuffer.allocate(8 + cipher.iv.size + encrypted.size)
                    .putInt(0x42544B31).putInt(cipher.iv.size).put(cipher.iv).put(encrypted).array()
                val output = wrapped.startWrite()
                try { output.write(blob); wrapped.finishWrite(output) }
                catch (error: Exception) { wrapped.failWrite(output); throw error }
                check(databaseDirectory.mkdirs() || databaseDirectory.isDirectory)
                check(filesDirectory.mkdirs() || filesDirectory.isDirectory)
                raw.copyOf()
            } finally { raw.fill(0) }
        }
    } catch (_: Exception) { throw SessionStorageFailure() }

    private fun createWrappingKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        generateKey()
    }

    /** Called solely by the owner after confirmed logout + authorizationStateClosed. */
    @Synchronized
    internal fun eraseAfterConfirmedLogout() {
        try {
            keyStore().deleteEntry(alias)
            check(!directory.exists() || directory.deleteRecursively())
        } catch (_: Exception) { throw SessionStorageFailure() }
    }
}
class SessionStorageFailure : Exception("Protected session storage is unavailable")
