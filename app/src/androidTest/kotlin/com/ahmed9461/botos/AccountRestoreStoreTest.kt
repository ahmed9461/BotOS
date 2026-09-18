package com.ahmed9461.botos

import androidx.test.platform.app.InstrumentationRegistry
import com.ahmed9461.botos.data.AccountRestoreStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Real DataStore files, isolated from both the live permission file and WorkspaceStore. */
class AccountRestoreStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun consentPersistsAndLogoutRevokesIt() = runBlocking<Unit> {
        val file = File(context.noBackupFilesDir, "restore-test-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        try {
            val first = AccountRestoreStore(file, CoroutineScope(job + Dispatchers.IO))
            assertFalse(first.mayRestore())
            first.setMayRestore(true)
            assertTrue(first.mayRestore())
            job.cancelAndJoin()
            job = SupervisorJob()
            val second = AccountRestoreStore(file, CoroutineScope(job + Dispatchers.IO))
            assertTrue(second.mayRestore())
            second.setMayRestore(false)
            assertFalse(second.mayRestore())
        } finally { job.cancelAndJoin(); file.delete() }
    }

    @Test fun restorePermissionBelongsToNoBackupAndDoesNotTouchBookmarks() = runBlocking<Unit> {
        assertEquals(context.noBackupFilesDir.canonicalFile, AccountRestoreStore.defaultFile(context).parentFile?.canonicalFile)
        val marker = File(context.filesDir, "workspace-marker-${UUID.randomUUID()}")
        val file = File(context.noBackupFilesDir, "restore-test-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        try {
            marker.writeText("must-stay")
            val store = AccountRestoreStore(file, CoroutineScope(job + Dispatchers.IO))
            store.setMayRestore(true)
            store.setMayRestore(false)
            assertEquals("must-stay", marker.readText())
            assertNotEquals(AccountRestoreStore.defaultFile(context).canonicalFile, file.canonicalFile)
        } finally { job.cancelAndJoin(); file.delete(); marker.delete() }
    }
}
