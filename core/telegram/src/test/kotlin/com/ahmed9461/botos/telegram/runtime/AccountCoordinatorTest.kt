package com.ahmed9461.botos.telegram.runtime

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic sessions only. These tests must never initialize a native client with test keys. */
class AccountCoordinatorTest {
    private class Preferences(var allowed: Boolean = false) : ConnectionPreferences {
        var reads = 0
        var write: suspend (Boolean) -> Unit = { allowed = it }
        override suspend fun mayRestore(): Boolean { reads++; return allowed }
        override suspend fun setMayRestore(value: Boolean) { write(value) }
    }
    private class Session(val userId: Long = 7) : AccountSession {
        override val step = MutableStateFlow(AuthStep.PARAMETERS)
        val submitted = mutableListOf<Pair<AuthStep, String>>()
        var closes = 0
        var logouts = 0
        var init: suspend () -> Unit = { step.value = AuthStep.PHONE }
        var logout: suspend () -> Unit = { step.value = AuthStep.CLOSED }
        var identity: suspend () -> JsonObject = {
            TdJson.command("user") { put("id", userId); put("first_name", "Synthetic name") }
        }
        override val rpc = object : TdRpc {
            override suspend fun request(command: JsonObject, timeoutMillis: Long): JsonObject {
                check(command.type() == "getMe")
                return identity()
            }
            override fun observeUpdates(observer: (JsonObject) -> Unit) = AutoCloseable {}
            override suspend fun awaitClosed(timeoutMillis: Long) { step.first { it == AuthStep.CLOSED } }
        }
        override suspend fun initialize(credentials: TelegramAppCredentials) { init() }
        override suspend fun submit(expectedStep: AuthStep, value: String) { submitted.add(expectedStep to value) }
        override suspend fun close() { closes++; step.value = AuthStep.CLOSED }
        override suspend fun logOut() { logouts++; logout() }
    }
    private class Fixture(configured: Boolean = true, val prefs: Preferences = Preferences()) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val fake = Session()
        var opens = 0
        var factory: suspend () -> AccountSession = { fake }
        val coordinator = AccountCoordinator(
            if (configured) TelegramAppCredentials(1, "0".repeat(32)) else null,
            AccountSessionFactory { opens++; factory() }, prefs, scope,
        )
        suspend fun waitStep(step: AuthStep) = withTimeout(2_000) { coordinator.state.first { it.step == step } }
        suspend fun ready() {
            fake.step.value = AuthStep.READY
            withTimeout(2_000) { coordinator.ready.first { it != null } }
        }
        override fun close() { scope.cancel() }
    }

    @Test fun noConfigurationNeverOpensOrReadsRestorePermission() = runBlocking {
        Fixture(configured = false).use { f ->
            f.coordinator.restore(); f.coordinator.connect(true)
            assertEquals(0, f.opens); assertEquals(0, f.prefs.reads)
            assertEquals(AccountIssue.CONFIGURATION, f.coordinator.state.value.issue)
        }
    }
    @Test fun explicitConsentIsRequiredBeforeOpeningAClient() = runBlocking {
        Fixture().use { f ->
            f.coordinator.connect(false)
            assertEquals(0, f.opens); assertEquals(AccountIssue.CONSENT, f.coordinator.state.value.issue)
            f.coordinator.connect(true); f.waitStep(AuthStep.PHONE)
            assertEquals(1, f.opens); assertFalse(f.prefs.allowed)
        }
    }
    @Test fun freshInstallDoesNotRestoreAndRestoreCheckRunsOnlyOnce() = runBlocking {
        Fixture().use { f ->
            f.coordinator.restore(); f.coordinator.restore()
            assertEquals(0, f.opens); assertEquals(1, f.prefs.reads)
        }
    }
    @Test fun previousPermissionRestoresButDoesNotInventLoginSuccess() = runBlocking {
        Fixture(prefs = Preferences(true)).use { f ->
            f.coordinator.restore(); f.waitStep(AuthStep.PHONE)
            assertEquals(1, f.opens); assertNull(f.coordinator.ready.value)
        }
    }
    @Test fun okDoesNotAdvanceCodeAndStaleSubmissionDoesNotReachSession() = runBlocking {
        Fixture().use { f ->
            f.coordinator.connect(true)
            f.fake.step.value = AuthStep.CODE; f.waitStep(AuthStep.CODE)
            f.coordinator.submit(AuthStep.CODE, "synthetic-code")
            assertEquals(AuthStep.CODE, f.coordinator.state.value.step)
            f.fake.step.value = AuthStep.PASSWORD; f.waitStep(AuthStep.PASSWORD)
            f.coordinator.submit(AuthStep.CODE, "stale-code")
            assertEquals(1, f.fake.submitted.size)
            assertEquals(AccountIssue.STATE, f.coordinator.state.value.issue)
            assertFalse(f.coordinator.state.value.toString().contains("synthetic-code"))
        }
    }
    @Test fun identityRequiresReadyAndEnablesFutureRestoreOnlyThen() = runBlocking {
        Fixture().use { f ->
            f.coordinator.connect(true); assertFalse(f.prefs.allowed)
            f.ready()
            assertTrue(f.prefs.allowed); assertEquals(7L, f.coordinator.ready.value?.userId)
            assertFalse(f.coordinator.state.value.toString().contains("Synthetic name"))
        }
    }
    @Test fun identityTimeoutIsReportedWithoutInventingAnAccountIdentity() = runBlocking {
        Fixture().use { f ->
            f.coordinator.connect(true)
            f.fake.identity = { withTimeout(1) { awaitCancellation() } }
            f.fake.step.value = AuthStep.READY
            withTimeout(2_000) { f.coordinator.state.first { it.issue == AccountIssue.CONNECTION } }
            assertNull(f.coordinator.ready.value); assertFalse(f.prefs.allowed)
        }
    }
    @Test fun nativeFactoryFailureReturnsToARecoverableClosedState() = runBlocking {
        Fixture().use { f ->
            f.factory = { throw TdFailure(FailureKind.NATIVE) }
            f.coordinator.connect(true)
            assertEquals(AuthStep.CLOSED, f.coordinator.state.value.step)
            assertEquals(AccountIssue.CONNECTION, f.coordinator.state.value.issue)
            f.factory = { f.fake }; f.coordinator.connect(true); f.waitStep(AuthStep.PHONE)
        }
    }
    @Test fun initializationFailureClosesButNeverLogsOutOrErases() = runBlocking {
        Fixture().use { f ->
            f.fake.init = { throw TdFailure(FailureKind.REMOTE, 500) }
            f.coordinator.connect(true)
            assertEquals(1, f.fake.closes); assertEquals(0, f.fake.logouts)
            assertEquals(AuthStep.CLOSED, f.coordinator.state.value.step)
        }
    }
    @Test fun cancellationClosesUnfinishedLoginWithoutRevocation() = runBlocking {
        Fixture().use { f ->
            val entered = CompletableDeferred<Unit>()
            f.fake.init = { entered.complete(Unit); awaitCancellation() }
            val attempt = launch { f.coordinator.connect(true) }
            withTimeout(2_000) { entered.await() }; attempt.cancelAndJoin()
            assertEquals(1, f.fake.closes); assertEquals(0, f.fake.logouts)
            assertFalse(f.coordinator.state.value.busy)
        }
    }
    @Test fun concurrentConnectDoesNotCreateTwoOwners() = runBlocking {
        Fixture().use { f ->
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            f.fake.init = { entered.complete(Unit); release.await(); f.fake.step.value = AuthStep.PHONE }
            val first = launch { f.coordinator.connect(true) }
            withTimeout(2_000) { entered.await() }; f.coordinator.connect(true)
            assertEquals(1, f.opens); assertEquals(AccountIssue.BUSY, f.coordinator.state.value.issue)
            release.complete(Unit); first.join(); f.waitStep(AuthStep.PHONE)
            assertFalse(f.coordinator.state.value.busy)
        }
    }
    @Test fun failedLogoutDisablesRestoreButDoesNotPretendToBeClosed() = runBlocking {
        Fixture().use { f ->
            f.coordinator.connect(true); f.ready()
            f.fake.logout = { throw TdFailure(FailureKind.REMOTE, 500) }
            f.coordinator.logOut()
            assertFalse(f.prefs.allowed); assertEquals(0, f.fake.closes)
            assertEquals(AuthStep.READY, f.coordinator.state.value.step)
            assertEquals(AccountIssue.CONNECTION, f.coordinator.state.value.issue)
        }
    }
    @Test fun successfulLogoutClearsReadyAndOldSessionCannotOverrideNewOne() = runBlocking {
        Fixture().use { f ->
            f.coordinator.connect(true); f.ready(); f.coordinator.logOut()
            assertFalse(f.prefs.allowed); assertNull(f.coordinator.ready.value)
            assertEquals(AuthStep.CLOSED, f.coordinator.state.value.step)
            val second = Session(8); f.factory = { second }
            f.coordinator.connect(true); f.waitStep(AuthStep.PHONE)
            f.fake.step.value = AuthStep.READY
            second.step.value = AuthStep.CODE; f.waitStep(AuthStep.CODE)
            assertNull(f.coordinator.ready.value)
        }
    }
    @Test fun cleanupFailureAfterClosedIsNotReportedAsSuccessfulErasure() = runBlocking {
        Fixture().use { f ->
            f.coordinator.connect(true); f.ready()
            f.fake.logout = { f.fake.step.value = AuthStep.CLOSED; error("Synthetic cleanup failure") }
            f.coordinator.logOut()
            assertEquals(AccountIssue.CLEANUP, f.coordinator.state.value.issue)
            assertNull(f.coordinator.ready.value); assertFalse(f.prefs.allowed)
        }
    }
    @Test fun permissionWriteAndLogoutAreSerializedSoIdentityCannotReenableRestore() = runBlocking {
        Fixture().use { f ->
            f.coordinator.connect(true)
            val writing = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            f.prefs.write = { value ->
                if (value) { writing.complete(Unit); release.await() }
                f.prefs.allowed = value
            }
            f.fake.step.value = AuthStep.READY; withTimeout(2_000) { writing.await() }
            val exiting = launch { f.coordinator.logOut() }
            yield(); release.complete(Unit); exiting.join()
            assertFalse(f.prefs.allowed); assertNull(f.coordinator.ready.value)
        }
    }
}
