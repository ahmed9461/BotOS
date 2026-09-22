package com.ahmed9461.botos

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahmed9461.botos.data.LocalAvatarStore
import com.ahmed9461.botos.model.SavedBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AvatarStoreTest {
    private val key = LocalAvatarStore.key(SavedBot("fixture", "fixture_bot", "تجريبي"))
    private fun image(): ByteArray {
        val bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(0xff6570a4.toInt())
            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
    private fun directory() = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "avatar-test-${UUID.randomUUID()}")
    @Test fun importIsResizedAndSurvivesReopeningWithoutOriginalUri() = runBlocking<Unit> {
        val root = directory()
        try {
            val first = LocalAvatarStore(root)
            first.synchronize(setOf(key)); first.replace(key) { ByteArrayInputStream(image()) }
            val version = first.versions.value.getValue(key)
            val restored = LocalAvatarStore(root)
            restored.synchronize(setOf(key))
            assertEquals(version, restored.versions.value[key])
            val bitmap = restored.decode(key, version)!!
            assertTrue(bitmap.width <= 160 && bitmap.height <= 160)
            bitmap.recycle()
            assertEquals(1, root.listFiles()!!.size)
            assertTrue(root.listFiles()!!.single().length() <= LocalAvatarStore.MAX_STORED)
        } finally { root.deleteRecursively() }
    }
    @Test fun invalidImportKeepsLastGoodImageAndRemovesStagingFiles() = runBlocking<Unit> {
        val root = directory()
        try {
            val store = LocalAvatarStore(root); store.synchronize(setOf(key))
            store.replace(key) { ByteArrayInputStream(image()) }
            val before = store.versions.value
            var rejected = false
            try { store.replace(key) { ByteArrayInputStream("not an image".toByteArray()) } }
            catch (_: Exception) { rejected = true }
            assertTrue(rejected); assertEquals(before, store.versions.value)
            assertEquals(1, root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }
    @Test fun oversizedStreamIsRejectedWithoutAllocatingTheWholeInput() = runBlocking<Unit> {
        val root = directory()
        try {
            val store = LocalAvatarStore(root); store.synchronize(setOf(key))
            var consumed = 0L
            val source = object : InputStream() {
                override fun read(): Int { consumed++; return 0 }
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int { consumed += length; return length }
            }
            var rejected = false
            try { store.replace(key) { source } } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
            assertTrue(consumed <= LocalAvatarStore.MAX_INPUT + 32 * 1024)
            assertTrue(store.versions.value.isEmpty()); assertTrue(root.listFiles()!!.isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun resetRemovesOverrideAndReopeningDoesNotRestoreIt() = runBlocking<Unit> {
        val root = directory()
        try {
            val store = LocalAvatarStore(root); store.synchronize(setOf(key))
            store.replace(key) { ByteArrayInputStream(image()) }; store.remove(key)
            assertTrue(store.versions.value.isEmpty())
            val reopened = LocalAvatarStore(root); reopened.synchronize(setOf(key))
            assertTrue(reopened.versions.value.isEmpty()); assertTrue(root.listFiles()!!.isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun renamedOrDeletedBookmarkCannotKeepOrAcquireThePreviousPicture() = runBlocking<Unit> {
        val root = directory()
        try {
            val store = LocalAvatarStore(root); store.synchronize(setOf(key))
            store.replace(key) { ByteArrayInputStream(image()) }
            val other = LocalAvatarStore.key(SavedBot("fixture", "another_bot", "آخر"))
            store.synchronize(setOf(other))
            assertTrue(store.versions.value.isEmpty()); assertTrue(root.listFiles()!!.isEmpty())
            var rejected = false
            try { store.replace(key) { ByteArrayInputStream(image()) } } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
        } finally { root.deleteRecursively() }
    }
    @Test fun callsFromMainMoveFileAndDecoderWorkOffTheUiThread() = runBlocking<Unit> {
        val root = directory()
        try {
            val store = LocalAvatarStore(root)
            withContext(Dispatchers.Main) {
                store.synchronize(setOf(key))
                store.replace(key) {
                    assertFalse(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
                    ByteArrayInputStream(image())
                }
                val bitmap = store.decode(key, store.versions.value.getValue(key))!!
                bitmap.recycle()
            }
        } finally { root.deleteRecursively() }
    }
}
