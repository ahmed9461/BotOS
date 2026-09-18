package com.ahmed9461.botos.tdlib

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android Keystore and private files; test-only slots, never a user session. */
@RunWith(AndroidJUnit4::class)
class SessionVaultTest {
    private fun vaultTest(body: (SessionVault, String) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val slot = "test_${UUID.randomUUID().toString().replace("-", "")}"
        val vault = SessionVault(context, slot)
        try { body(vault, slot) } finally { vault.eraseAfterConfirmedLogout() }
    }
    private fun rejected(body: () -> Unit) {
        try { body(); fail("Corrupt or missing protected data must fail closed") }
        catch (error: SessionStorageFailure) { assertNull(error.cause) }
    }
    @Test fun keySurvivesNewInstanceWithoutPlaintextOnDisk() = vaultTest { vault, slot ->
        val first = vault.loadOrCreate()
        val rawFile = File(vault.directory, "database-key.bin").readBytes()
        assertEquals(32, first.size); assertEquals(68, rawFile.size)
        assertFalse(rawFile.toList().windowed(32).any { it == first.toList() })
        val restored = SessionVault(InstrumentationRegistry.getInstrumentation().targetContext, slot).loadOrCreate()
        assertArrayEquals(first, restored)
        assertTrue(vault.directory.canonicalPath.startsWith(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir.canonicalPath + File.separator))
        first.fill(0); restored.fill(0)
    }
    @Test fun corruptedCiphertextIsNotReset() = vaultTest { vault, _ ->
        vault.loadOrCreate().fill(0)
        val file = File(vault.directory, "database-key.bin")
        val altered = file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        file.writeBytes(altered)
        rejected { vault.loadOrCreate() }
        assertArrayEquals(altered, file.readBytes())
    }
    @Test fun lostKeystoreKeyDoesNotReplaceStoredSession() = vaultTest { vault, _ ->
        vault.loadOrCreate().fill(0)
        val file = File(vault.directory, "database-key.bin"); val before = file.readBytes()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(vault.alias) }
        rejected { vault.loadOrCreate() }
        assertArrayEquals(before, file.readBytes())
    }
    @Test fun lostWrappedKeyWithDatabasePresentDoesNotGenerateAnother() = vaultTest { vault, _ ->
        vault.loadOrCreate().fill(0)
        val database = File(vault.databaseDirectory, "synthetic-db")
        database.writeText("not a real account")
        assertTrue(File(vault.directory, "database-key.bin").delete())
        rejected { vault.loadOrCreate() }
        assertEquals("not a real account", database.readText())
        assertFalse(File(vault.directory, "database-key.bin").exists())
    }
    @Test fun unexpectedFileLengthFailsWithoutReplacement() = vaultTest { vault, _ ->
        vault.loadOrCreate().fill(0)
        val file = File(vault.directory, "database-key.bin"); file.appendBytes(byteArrayOf(0))
        rejected { vault.loadOrCreate() }; assertEquals(69L, file.length())
    }
    @Test fun testSlotErasureLeavesAnUnrelatedSlotUntouched() = vaultTest { vault, _ ->
        vault.loadOrCreate().fill(0)
        vaultTest { other, _ ->
            val otherKey = other.loadOrCreate()
            vault.eraseAfterConfirmedLogout()
            assertFalse(vault.directory.exists())
            val restored = other.loadOrCreate(); assertArrayEquals(otherKey, restored)
            otherKey.fill(0); restored.fill(0)
        }
    }
}
