package com.ahmed9461.botos.telegram.runtime

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Implemented by the Android runtime; test implementations never use the network. */
interface AccountSession {
    val step: StateFlow<AuthStep>
    val rpc: TdRpc
    suspend fun initialize(credentials: TelegramAppCredentials)
    suspend fun submit(expectedStep: AuthStep, value: String)
    suspend fun close()
    suspend fun logOut()
}
fun interface AccountSessionFactory { suspend fun open(): AccountSession }
interface ConnectionPreferences {
    suspend fun mayRestore(): Boolean
    suspend fun setMayRestore(value: Boolean)
}

enum class AccountIssue { CONFIGURATION, CONSENT, INPUT, BUSY, CONNECTION, STORAGE, CLEANUP, STATE }
data class AccountState(
    val configured: Boolean,
    val step: AuthStep = AuthStep.CLOSED,
    val busy: Boolean = false,
    val issue: AccountIssue? = null,
    val displayName: String = "",
) {
    // Do not accidentally include account information in a diagnostic log.
    override fun toString() = "AccountState(configured=$configured, step=$step, busy=$busy, issue=$issue)"
}
internal class ReadyAccount(val generation: Long, val userId: Long, val rpc: TdRpc) {
    override fun toString() = "ReadyAccount([redacted])"
}

/** One process-owned coordinator. Inputs are consumed transiently, never saved in UI state. */
class AccountCoordinator(
    private val credentials: TelegramAppCredentials?,
    private val factory: AccountSessionFactory,
    private val preferences: ConnectionPreferences,
    private val scope: CoroutineScope,
) {
    private val operation = Mutex()
    private val generation = AtomicLong(0)
    private val restoreChecked = AtomicBoolean(false)
    private val restoreWrites = Mutex()
    @Volatile private var allowRestore = false
    @Volatile private var session: AccountSession? = null
    private var observer: Job? = null
    private val _state = MutableStateFlow(AccountState(configured = credentials != null))
    val state: StateFlow<AccountState> = _state.asStateFlow()
    private val _ready = MutableStateFlow<ReadyAccount?>(null)
    internal val ready: StateFlow<ReadyAccount?> = _ready.asStateFlow()

    /** Called once at application startup. A fresh install never opens a Telegram client. */
    suspend fun restore() {
        if (credentials == null || !restoreChecked.compareAndSet(false, true)) return
        val initialGeneration = generation.get()
        val allowed = try { preferences.mayRestore() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { issue(AccountIssue.STORAGE); return }
        if (allowed) begin(consentAccepted = true, expectedGeneration = initialGeneration)
    }

    suspend fun connect(consentAccepted: Boolean) = begin(consentAccepted, expectedGeneration = null)

    private suspend fun begin(consentAccepted: Boolean, expectedGeneration: Long?) = exclusive {
        if (expectedGeneration != null && generation.get() != expectedGeneration) return@exclusive
        val configuration = credentials ?: run { issue(AccountIssue.CONFIGURATION); return@exclusive }
        if (!consentAccepted) { issue(AccountIssue.CONSENT); return@exclusive }
        val existing = session
        if (existing != null) {
            if (existing.step.value != AuthStep.CLOSED) { issue(AccountIssue.STATE); return@exclusive }
            // Closing an old object must complete before a new native owner is created.
            existing.close()
            observer?.cancelAndJoin()
            session = null
        }
        val token = generation.incrementAndGet()
        allowRestore = true
        _ready.value = null
        _state.update { it.copy(step = AuthStep.STARTING, displayName = "") }
        val opened = try { factory.open() }
        catch (failure: Throwable) {
            _state.update { it.copy(step = AuthStep.CLOSED) }
            throw failure
        }
        session = opened
        observer = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            opened.step.collectLatest { step ->
                if (!isCurrent(token, opened)) return@collectLatest
                if (step != AuthStep.READY) _ready.value = null
                _state.update { it.copy(step = step, displayName = if (step == AuthStep.READY) it.displayName else "") }
                if (step == AuthStep.READY) loadIdentity(token, opened)
            }
        }
        try {
            withTimeout(60_000) { opened.initialize(configuration) }
        } catch (failure: Throwable) {
            // Keep encrypted files. Cancellation is not a server logout or permission to delete.
            withContext(NonCancellable) {
                try {
                    withTimeout(20_000) { opened.close() }
                    observer?.cancelAndJoin()
                    session = null
                    _ready.value = null
                    _state.update { it.copy(step = AuthStep.CLOSED, displayName = "") }
                } catch (_: Exception) {
                    // Retain the handle so the user can retry closing rather than creating two owners.
                    _state.update { it.copy(step = AuthStep.CLOSING) }
                }
            }
            throw failure
        }
    }

    suspend fun submit(expectedStep: AuthStep, value: String) = exclusive {
        val current = session ?: throw TdFailure(FailureKind.CLOSED)
        if (current.step.value != expectedStep || expectedStep !in inputSteps) throw TdFailure(FailureKind.WRONG_STATE)
        current.submit(expectedStep, value)
        // No locally invented next step: only the native authorization update may advance it.
    }

    suspend fun retryIdentity() = exclusive {
        val current = session ?: throw TdFailure(FailureKind.CLOSED)
        if (current.step.value != AuthStep.READY) throw TdFailure(FailureKind.WRONG_STATE)
        loadIdentity(generation.get(), current)
    }

    /** Cancel an unfinished sign-in. This preserves files and does not claim account revocation. */
    suspend fun cancelLogin() = exclusive {
        val current = session ?: return@exclusive
        if (current.step.value == AuthStep.READY) throw TdFailure(FailureKind.WRONG_STATE)
        restoreWrites.withLock {
            allowRestore = false
            preferences.setMayRestore(false)
        }
        current.close()
        observer?.cancelAndJoin()
        generation.incrementAndGet()
        session = null
        _ready.value = null
        _state.update { it.copy(step = AuthStep.CLOSED, displayName = "") }
    }

    /** An explicit logout disables auto-restore before attempting revocation and local erasure. */
    suspend fun logOut() = exclusive {
        val current = session ?: throw TdFailure(FailureKind.CLOSED)
        if (current.step.value != AuthStep.READY) throw TdFailure(FailureKind.WRONG_STATE)
        restoreWrites.withLock {
            allowRestore = false
            preferences.setMayRestore(false)
        }
        try {
            current.logOut()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (current.step.value == AuthStep.CLOSED) {
                observer?.cancelAndJoin()
                session = null
                _ready.value = null
                _state.update { it.copy(step = AuthStep.CLOSED, displayName = "", issue = AccountIssue.CLEANUP) }
                return@exclusive
            }
            throw failure
        }
        observer?.cancelAndJoin()
        generation.incrementAndGet()
        session = null
        _ready.value = null
        _state.update { it.copy(step = AuthStep.CLOSED, displayName = "") }
    }

    private suspend fun loadIdentity(token: Long, current: AccountSession) {
        try {
            val user = current.rpc.request(TdJson.command("getMe"))
            val id = user.number("id")
            if (user.type() != "user" || id == null || id <= 0) throw TdFailure(FailureKind.PROTOCOL)
            if (!isCurrent(token, current) || current.step.value != AuthStep.READY) return
            // A restore flag is permission, not an authentication credential or login proof.
            try {
                restoreWrites.withLock {
                    if (allowRestore && isCurrent(token, current) && current.step.value == AuthStep.READY) {
                        preferences.setMayRestore(true)
                    }
                }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { issue(AccountIssue.STORAGE) }
            if (!isCurrent(token, current) || current.step.value != AuthStep.READY) return
            _ready.value = ReadyAccount(token, id, current.rpc)
            _state.update { it.copy(displayName = user.string("first_name").take(128)) }
        } catch (_: TimeoutCancellationException) {
            if (isCurrent(token, current)) issue(AccountIssue.CONNECTION)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (isCurrent(token, current)) issue(AccountIssue.CONNECTION) }
    }

    private fun isCurrent(token: Long, current: AccountSession) = generation.get() == token && session === current
    private fun issue(value: AccountIssue) { _state.update { it.copy(issue = value) } }
    private suspend fun exclusive(block: suspend () -> Unit) {
        if (!operation.tryLock()) { issue(AccountIssue.BUSY); return }
        _state.update { it.copy(busy = true, issue = null) }
        try { block() }
        catch (_: TimeoutCancellationException) { issue(AccountIssue.CONNECTION) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: TdFailure) {
            issue(when (failure.kind) {
                FailureKind.BAD_INPUT -> AccountIssue.INPUT
                FailureKind.BUSY -> AccountIssue.BUSY
                FailureKind.NOT_CONFIGURED -> AccountIssue.CONFIGURATION
                FailureKind.WRONG_STATE, FailureKind.CLOSED -> AccountIssue.STATE
                else -> AccountIssue.CONNECTION
            })
        }
        catch (_: Exception) { issue(AccountIssue.STORAGE) }
        finally {
            _state.update { it.copy(busy = false, issue = it.issue.takeUnless { value -> value == AccountIssue.BUSY }) }
            operation.unlock()
        }
    }
    companion object {
        val inputSteps = setOf(AuthStep.PHONE, AuthStep.EMAIL, AuthStep.EMAIL_CODE, AuthStep.CODE, AuthStep.PASSWORD)
    }
}
