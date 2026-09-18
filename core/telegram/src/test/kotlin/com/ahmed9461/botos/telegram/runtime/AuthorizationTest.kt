package com.ahmed9461.botos.telegram.runtime

import java.util.Base64
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

private class FakeRpc : TdRpc {
    var observer: ((JsonObject) -> Unit)? = null
    val requests = mutableListOf<JsonObject>()
    var response: suspend () -> JsonObject = { TdJson.command("ok") }
    var closing: suspend () -> Unit = { auth("authorizationStateClosed") }
    override suspend fun request(command: JsonObject, timeoutMillis: Long): JsonObject { requests.add(command); return response() }
    override fun observeUpdates(observer: (JsonObject) -> Unit): AutoCloseable {
        this.observer = observer; return AutoCloseable { this.observer = null }
    }
    override suspend fun awaitClosed(timeoutMillis: Long) { closing() }
    fun auth(type: String) { observer?.invoke(TdJson.command("updateAuthorizationState") { put("authorization_state", TdJson.command(type)) }) }
}
class AuthorizationTest {
    @Test fun okIsNotLoginSuccess() = runBlocking {
        val rpc = FakeRpc(); val auth = Authorization(rpc)
        rpc.auth("authorizationStateWaitCode"); auth.code("synthetic")
        assertEquals(AuthStep.CODE, auth.step.value)
        rpc.auth("authorizationStateReady"); assertEquals(AuthStep.READY, auth.step.value)
        auth.close()
    }
    @Test fun invalidStageAndInvalidPhoneNeverSend() = runBlocking {
        val rpc = FakeRpc(); val auth = Authorization(rpc)
        try { auth.phone("+123456789"); fail("Wrong state") } catch (e: TdFailure) { assertEquals(FailureKind.WRONG_STATE, e.kind) }
        rpc.auth("authorizationStateWaitPhoneNumber")
        try { auth.phone("not-a-number"); fail("Bad input") } catch (e: TdFailure) { assertEquals(FailureKind.BAD_INPUT, e.kind) }
        assertTrue(rpc.requests.isEmpty()); auth.close()
    }
    @Test fun parametersMatchPinnedSchemaAndEncodeBinaryKey() = runBlocking {
        val rpc = FakeRpc(); val auth = Authorization(rpc); rpc.auth("authorizationStateWaitTdlibParameters")
        val credentials = TelegramAppCredentials(1, "0".repeat(32))
        val parameters = TdParameters(credentials, "/private/db", "/private/files", "ar", "test", "35", "test")
        val key = ByteArray(32) { it.toByte() }
        auth.initialize(parameters, key)
        val command = rpc.requests.single()
        assertEquals("setTdlibParameters", command.type())
        assertArrayEquals(key, Base64.getDecoder().decode(command.string("database_encryption_key")))
        assertEquals(false, command["use_secret_chats"]?.jsonPrimitive?.boolean)
        assertEquals(AuthStep.PARAMETERS, auth.step.value)
        assertFalse(parameters.toString().contains("/private")); assertFalse(credentials.toString().contains("0".repeat(32)))
        key.fill(0); auth.close()
    }
    @Test fun concurrentAuthorizationSubmissionsAreRejected() = runBlocking {
        val rpc = FakeRpc(); val auth = Authorization(rpc); rpc.auth("authorizationStateWaitCode")
        val gate = CompletableDeferred<Unit>(); rpc.response = { gate.await(); TdJson.command("ok") }
        val first = async { auth.code("synthetic") }; yield()
        try { auth.code("synthetic-2"); fail("Expected busy") } catch (e: TdFailure) { assertEquals(FailureKind.BUSY, e.kind) }
        gate.complete(Unit); first.await(); assertEquals(1, rpc.requests.size); auth.close()
    }
    @Test fun logoutRequiresClosedAndIsNotLocalClose() = runBlocking {
        val rpc = FakeRpc(); val auth = Authorization(rpc); rpc.auth("authorizationStateReady")
        auth.logOut()
        assertEquals("logOut", rpc.requests.single().type()); assertEquals(AuthStep.CLOSED, auth.step.value)
        auth.close()
    }
    @Test fun failedLogoutCannotClaimSessionWasRevoked() = runBlocking {
        val rpc = FakeRpc(); val auth = Authorization(rpc); rpc.auth("authorizationStateReady")
        rpc.response = { throw TdFailure(FailureKind.REMOTE, 500) }
        var awaited = false; rpc.closing = { awaited = true }
        try { auth.logOut(); fail("Should fail") } catch (e: TdFailure) { assertEquals(FailureKind.REMOTE, e.kind) }
        assertFalse(awaited); assertEquals(AuthStep.READY, auth.step.value); auth.close()
    }
    @Test fun uncommonStatesNeverBecomeReadyOrCollectUnneededInput() {
        val rpc = FakeRpc(); val auth = Authorization(rpc)
        val states = mapOf("authorizationStateWaitRegistration" to AuthStep.REGISTRATION_REQUIRED,
            "authorizationStateWaitPremiumPurchase" to AuthStep.PREMIUM_REQUIRED,
            "authorizationStateWaitOtherDeviceConfirmation" to AuthStep.OTHER_DEVICE,
            "futureAuthorizationState" to AuthStep.UNSUPPORTED)
        states.forEach { (wire, expected) -> rpc.auth(wire); assertEquals(expected, auth.step.value) }
        assertTrue(rpc.requests.isEmpty()); auth.close()
    }
    @Test fun emailCodeUsesTypedObjectAndPasswordIsNotPersisted() = runBlocking {
        val rpc = FakeRpc(); val auth = Authorization(rpc); rpc.auth("authorizationStateWaitEmailCode")
        auth.emailCode("synthetic-code")
        val nested = rpc.requests.single()["code"]!!.jsonObject
        assertEquals("emailAddressAuthenticationCode", nested.type())
        rpc.auth("authorizationStateWaitPassword"); auth.password("synthetic-password")
        assertEquals(AuthStep.PASSWORD, auth.step.value); auth.close()
        assertNull(rpc.observer)
    }
}
