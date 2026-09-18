package com.ahmed9461.botos.telegram.runtime

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

private class FakePort : TdJsonPort {
    val sent = LinkedBlockingQueue<JsonObject>()
    val incoming = LinkedBlockingQueue<String>()
    var autoClose = true
    override fun createClientId() = 7
    override fun send(clientId: Int, request: String) {
        val command = TdJson.parse(request)
        check(clientId == 7)
        when (command.type()) {
            "getAuthorizationState" -> update("updateAuthorizationState") {
                put("authorization_state", TdJson.command("authorizationStateWaitTdlibParameters"))
            }
            "close" -> if (autoClose) update("updateAuthorizationState") {
                put("authorization_state", TdJson.command("authorizationStateClosed"))
            }
            else -> sent.put(command)
        }
    }
    override fun receive(timeoutSeconds: Double): String? = incoming.poll((timeoutSeconds * 1000).toLong(), TimeUnit.MILLISECONDS)
    suspend fun next(): JsonObject = withContext(Dispatchers.IO) {
        sent.poll(3, TimeUnit.SECONDS) ?: error("Expected request was not sent")
    }
    fun reply(request: JsonObject, type: String = "ok", client: Int = 7, fields: JsonObjectBuilder.() -> Unit = {}) {
        incoming.put(TdJson.command(type) {
            put("@client_id", client); put("@extra", request.getValue("@extra")); fields()
        }.toString())
    }
    fun update(type: String, fields: JsonObjectBuilder.() -> Unit = {}) {
        incoming.put(TdJson.command(type) { put("@client_id", 7); fields() }.toString())
    }
}
private suspend fun expectFailure(kind: FailureKind, action: suspend () -> Unit) {
    try { action(); fail("Expected a sanitized failure") }
    catch (error: TdFailure) { assertEquals(kind, error.kind); assertNull(error.cause) }
}

class TdTransportTest {
    private fun test(body: suspend CoroutineScope.(FakePort, TdTransport) -> Unit) = runBlocking {
        val port = FakePort()
        val transport = TdTransport(port)
        transport.start()
        try { body(port, transport) }
        finally { withContext(NonCancellable) { port.autoClose = true; transport.close(3_000) } }
    }

    @Test fun correlatesReversedRepliesAndIgnoresWrongClient() = test { port, transport ->
        val a = async { transport.request(TdJson.command("getOption") { put("name", "version") }) }
        val first = port.next()
        val b = async { transport.request(TdJson.command("getOption") { put("name", "commit_hash") }) }
        val second = port.next()
        port.reply(first, "optionValueString", client = 999) { put("value", "wrong") }
        port.reply(second, "optionValueString") { put("value", "second") }
        assertEquals("second", b.await().string("value"))
        assertFalse(a.isCompleted)
        port.reply(first, "optionValueString") { put("value", "first") }
        assertEquals("first", a.await().string("value"))
        assertEquals(0, transport.pendingCount)
    }

    @Test fun timeoutDetachesWithoutRepeatingSideEffect() = test { port, transport ->
        val request = async {
            try { transport.request(TdJson.command("getCallbackQueryAnswer"), 80); false }
            catch (_: TimeoutCancellationException) { true }
        }
        val sent = port.next()
        assertTrue(request.await())
        assertEquals(0, transport.pendingCount)
        assertTrue(port.sent.isEmpty())
        port.reply(sent)
        val later = async { transport.request(TdJson.command("getOption")) }
        val next = port.next(); port.reply(next)
        assertEquals("ok", later.await().type())
    }

    @Test fun callerCancellationRemovesPendingAndDoesNotCloseClient() = test { port, transport ->
        val a = async { transport.request(TdJson.command("sendMessage")) }
        val sent = port.next(); a.cancelAndJoin()
        assertEquals(0, transport.pendingCount)
        port.reply(sent)
        val b = async { transport.request(TdJson.command("getOption")) }
        port.reply(port.next()); assertEquals("ok", b.await().type())
    }

    @Test fun remoteErrorDoesNotLeakItsMessageOrRequest() = test { port, transport ->
        val result = async {
            try { transport.request(TdJson.command("sendMessage")); null }
            catch (e: TdFailure) { e }
        }
        port.reply(port.next(), "error") { put("code", 400); put("message", "private-secret-content") }
        val error = result.await() ?: error("Missing expected failure")
        assertEquals(FailureKind.REMOTE, error.kind)
        assertEquals(400, error.code)
        assertFalse(error.toString().contains("private-secret-content"))
        assertNull(error.cause)
    }

    @Test fun updatesRemainOrderedAndSubscriptionCanBeRemoved() = test { port, transport ->
        val order = CopyOnWriteArrayList<Long>()
        val subscription = transport.observeUpdates { if (it.type() == "updateExample") order.add(it.number("order")!!) }
        repeat(20) { value -> port.update("updateExample") { put("order", value) } }
        withTimeout(3_000) { while (order.size != 20) delay(5) }
        assertEquals((0L..19L).toList(), order.toList())
        subscription.close()
        port.update("updateExample") { put("order", 100) }
        val sync = async { transport.request(TdJson.command("getOption")) }
        port.reply(port.next()); sync.await()
        assertEquals(20, order.size)
    }

    @Test fun closeFailsWaitersAndRejectsNewRequests() = test { port, transport ->
        val waiting = async { expectFailure(FailureKind.CLOSED) { transport.request(TdJson.command("sendMessage")) } }
        port.next()
        transport.close(3_000); waiting.await()
        assertEquals(0, transport.pendingCount)
        expectFailure(FailureKind.CLOSED) { transport.request(TdJson.command("sendMessage")) }
    }

    @Test fun capacityIsBoundedWithoutEvictingEarlierRequest() = runBlocking {
        val port = FakePort(); val transport = TdTransport(port, maxPending = 1); transport.start()
        try {
            val first = async { transport.request(TdJson.command("sendMessage")) }
            val sent = port.next()
            expectFailure(FailureKind.BUSY) { transport.request(TdJson.command("sendMessage")) }
            port.reply(sent); first.await(); Unit
        } finally { transport.close(3_000) }
    }

    @Test fun malformedWireDataFailsClosedWithoutLeakingInput() = test { port, transport ->
        val waiting = async { expectFailure(FailureKind.PROTOCOL) { transport.request(TdJson.command("sendMessage")) } }
        port.next(); port.incoming.put("sensitive invalid input")
        waiting.await(); transport.awaitClosed(3_000)
        assertEquals(0, transport.pendingCount)
    }

    @Test fun rejectsCallerCorrelationField() = test { _, transport ->
        try { transport.request(TdJson.command("getOption") { put("@extra", "forged") }); fail("Must reject") }
        catch (_: IllegalArgumentException) { assertEquals(0, transport.pendingCount) }
    }
}
