package com.ahmed9461.botos.telegram.runtime

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** One process-wide native receive owner. Implementations must not log wire data. */
interface TdJsonPort {
    fun createClientId(): Int
    fun send(clientId: Int, request: String)
    fun receive(timeoutSeconds: Double): String?
}
interface TdRpc {
    suspend fun request(command: JsonObject, timeoutMillis: Long = 30_000): JsonObject
    fun observeUpdates(observer: (JsonObject) -> Unit): AutoCloseable
    suspend fun awaitClosed(timeoutMillis: Long = 30_000)
}

/**
 * One receive loop applies updates synchronously in arrival order. Observers must not block.
 * Timeouts/cancellation only detach callers; they NEVER replay side-effecting requests.
 */
class TdTransport(
    private val port: TdJsonPort,
    private val maxPending: Int = 128,
    private val onNativeClosed: () -> Unit = {},
) : TdRpc {
    private val guard = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val observers = CopyOnWriteArrayList<(JsonObject) -> Unit>()
    private val ids = AtomicLong()
    private val closed = CompletableDeferred<Unit>()
    @Volatile private var clientId = 0
    @Volatile private var closing = false
    @Volatile private var started = false
    val pendingCount: Int get() = pending.size

    init { require(maxPending in 1..1024) }

    fun start() = synchronized(guard) {
        check(!started) { "Transport instances cannot be restarted" }
        started = true
        clientId = try { port.createClientId().also { check(it > 0) } }
        catch (_: Exception) { scope.cancel(); throw TdFailure(FailureKind.NATIVE) }
        scope.launch { receiveLoop() }
        // Kicks off authorization updates; this offline request does not authorize an account.
        try { port.send(clientId, TdJson.command("getAuthorizationState").toString()) }
        catch (_: Exception) { beginClose(TdFailure(FailureKind.NATIVE)) }
    }

    override fun observeUpdates(observer: (JsonObject) -> Unit): AutoCloseable {
        observers.add(observer)
        return AutoCloseable { observers.remove(observer) }
    }

    override suspend fun request(command: JsonObject, timeoutMillis: Long): JsonObject {
        require(timeoutMillis > 0)
        require(command.type().isNotEmpty() && "@extra" !in command && "@client_id" !in command)
        val extra = "botos:${ids.incrementAndGet()}"
        val answer = CompletableDeferred<JsonObject>()
        synchronized(guard) {
            if (!started || closing || closed.isCompleted) throw TdFailure(FailureKind.CLOSED)
            if (pending.size >= maxPending) throw TdFailure(FailureKind.BUSY)
            pending[extra] = answer // register before native send: replies may be immediate
            try { port.send(clientId, JsonObject(command + ("@extra" to JsonPrimitive(extra))).toString()) }
            catch (_: Exception) { pending.remove(extra); answer.completeExceptionally(TdFailure(FailureKind.NATIVE)) }
        }
        return try { withTimeout(timeoutMillis) { answer.await() } }
        finally { pending.remove(extra, answer); answer.cancel() }
    }

    override suspend fun awaitClosed(timeoutMillis: Long) { withTimeout(timeoutMillis) { closed.await() } }

    /** Flush/close only. Does not claim to revoke the Telegram authorization or wipe storage. */
    suspend fun close(timeoutMillis: Long = 30_000) {
        beginClose(TdFailure(FailureKind.CLOSED))
        awaitClosed(timeoutMillis)
    }

    private fun beginClose(reason: TdFailure) = synchronized(guard) {
        if (!started || closing || closed.isCompleted) return@synchronized
        closing = true
        pending.values.forEach { it.completeExceptionally(reason) }
        pending.clear()
        // A failed send does not free the native owner: only authorizationStateClosed does.
        try { port.send(clientId, TdJson.command("close").toString()) }
        catch (_: Exception) { /* owner remains reserved; caller's close timeout reports failure */ }
    }

    private suspend fun receiveLoop() {
        try {
            while (currentCoroutineContext().isActive && !closed.isCompleted) {
                val raw = try { port.receive(0.25) }
                catch (_: Exception) { beginClose(TdFailure(FailureKind.NATIVE)); delay(100); continue }
                if (raw == null) continue
                val value = try { TdJson.parse(raw) }
                catch (_: TdFailure) { beginClose(TdFailure(FailureKind.PROTOCOL)); continue }
                if (value.number("@client_id") != clientId.toLong()) continue
                val extra = value.string("@extra")
                if (extra.isNotEmpty()) {
                    pending.remove(extra)?.let { deferred ->
                        if (value.type() == "error") deferred.completeExceptionally(TdFailure(FailureKind.REMOTE, value.number("code")?.toInt()))
                        else deferred.complete(value)
                    }
                } else if (value.type().startsWith("update")) {
                    for (observer in observers) {
                        try { observer(value) }
                        catch (_: Exception) { beginClose(TdFailure(FailureKind.PROTOCOL)) }
                    }
                    val auth = value["authorization_state"] as? JsonObject
                    if (value.type() == "updateAuthorizationState" && auth?.type() == "authorizationStateClosed") {
                        closing = true
                        pending.values.forEach { it.completeExceptionally(TdFailure(FailureKind.CLOSED)) }
                        pending.clear()
                        observers.clear()
                        onNativeClosed()
                        closed.complete(Unit)
                        return
                    }
                }
            }
        } finally {
            if (closed.isCompleted) scope.cancel()
        }
    }
}
