package com.ahmed9461.botos.tdlib

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.ahmed9461.botos.telegram.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual JNI, without app credentials, a phone number or a Telegram account. */
@RunWith(AndroidJUnit4::class)
class NativeRuntimeTest {
    @Test fun loadsPinnedNativeAndRoundTripsUnicodeBeforeClosing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = AndroidTelegramSession.open(context)
        try {
            withTimeout(10_000) { session.authorization.step.first { it == AuthStep.PARAMETERS } }
            val version = session.transport.request(TdJson.command("getOption") { put("name", "version") })
            assertEquals("1.8.67", version.string("value"))
            val commit = session.transport.request(TdJson.command("getOption") { put("name", "commit_hash") })
            val text = "مرحبا 🌚 BotOS — أهلاً 𝄞"
            val response = session.transport.request(TdJson.command("testCallString") { put("x", text) })
            assertEquals("testString", response.type()); assertEquals(text, response.string("value"))
            assertEquals(AuthStep.PARAMETERS, session.authorization.step.value)
            PlatformTestStorageRegistry.getInstance().openOutputFile("native-runtime.txt").bufferedWriter().use {
                it.write("version=${version.string("value")}\ncommit=${commit.string("value")}\nunicodeRoundTrip=passed\nauthorization=waitTdlibParameters\naccountUsed=false\n")
            }
        } finally { withContext(NonCancellable) { session.close() } }
        assertEquals(AuthStep.CLOSED, session.authorization.step.value)
    }

    @Test fun processReceiveOwnerIsExclusiveAndReleasedOnlyAfterClose() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = AndroidTelegramSession.open(context)
        try {
            try { AndroidTelegramSession.open(context); fail("Second native receiver must be rejected") }
            catch (error: TdFailure) { assertEquals(FailureKind.BUSY, error.kind) }
        } finally { withContext(NonCancellable) { first.close() } }
        val second = AndroidTelegramSession.open(context)
        try {
            withTimeout(10_000) { second.authorization.step.first { it == AuthStep.PARAMETERS } }
            first.close()
            try { AndroidTelegramSession.open(context); fail("Stale close released another client's lease") }
            catch (error: TdFailure) { assertEquals(FailureKind.BUSY, error.kind) }
        }
        finally { withContext(NonCancellable) { second.close() } }
    }
}
