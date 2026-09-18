package com.ahmed9461.botos.tdlib

import android.content.Context
import android.os.Build
import com.ahmed9461.botos.telegram.runtime.*
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.put
import org.drinkless.tdlib.JsonClient

/**
 * Internal P2 runtime. It is not installed into the preview app and never starts from a screen.
 * Call open explicitly, then initialize only after the account owner accepts the login disclosure.
 */
class AndroidTelegramSession private constructor(
    private val vault: SessionVault,
    private val ownerToken: Any,
    val transport: TdTransport,
    val authorization: Authorization,
) {
    private val lifecycle = Mutex()
    suspend fun initialize(credentials: TelegramAppCredentials) = lifecycle.withLock {
        withTimeout(15_000) { authorization.step.first { it == AuthStep.PARAMETERS } }
        val key = withContext(Dispatchers.IO) { vault.loadOrCreate() }
        try {
            authorization.initialize(TdParameters(credentials, vault.databaseDirectory.absolutePath,
                vault.filesDirectory.absolutePath, Locale.getDefault().toLanguageTag(),
                Build.MODEL, Build.VERSION.RELEASE, "BotOS P2 runtime"), key)
        } finally { key.fill(0) }
    }
    /** Local close keeps the encrypted session available for next launch. */
    suspend fun close() = lifecycle.withLock {
        transport.close()
        authorization.close()
        nativeOwner.compareAndSet(ownerToken, null)
    }
    /** Never erase a session on network error, cancellation, or a local close. */
    suspend fun logOut() = lifecycle.withLock {
        authorization.logOut()
        // Retain the process owner until erasure ends; a new client must not reopen these files.
        try { withContext(Dispatchers.IO) { vault.eraseAfterConfirmedLogout() } }
        finally { authorization.close(); nativeOwner.compareAndSet(ownerToken, null) }
    }

    companion object {
        private val nativeOwner = AtomicReference<Any?>(null)
        /** Call off the UI thread. No account request is sent until initialize is explicit. */
        fun open(context: Context): AndroidTelegramSession {
            val ownerToken = Any()
            if (!nativeOwner.compareAndSet(null, ownerToken)) throw TdFailure(FailureKind.BUSY)
            try {
                // Explicit load first: catch missing ABI without the upstream fallback stack trace.
                System.loadLibrary("tdjsonjava")
                val logResult = JsonClient.execute(TdJson.command("setLogStream") {
                    put("log_stream", TdJson.command("logStreamEmpty"))
                }.toString())
                check(logResult != null && TdJson.parse(logResult).type() == "ok")
                val port = object : TdJsonPort {
                    override fun createClientId() = JsonClient.createClientId()
                    override fun send(clientId: Int, request: String) = JsonClient.send(clientId, request)
                    override fun receive(timeoutSeconds: Double) = JsonClient.receive(timeoutSeconds)
                }
                val transport = TdTransport(port)
                val auth = Authorization(transport)
                val result = AndroidTelegramSession(SessionVault(context.applicationContext), ownerToken, transport, auth)
                transport.start()
                return result
            } catch (_: LinkageError) { nativeOwner.compareAndSet(ownerToken, null); throw TdFailure(FailureKind.NATIVE) }
            catch (_: Exception) { nativeOwner.compareAndSet(ownerToken, null); throw TdFailure(FailureKind.NATIVE) }
        }
    }
}
