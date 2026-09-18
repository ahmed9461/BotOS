package com.ahmed9461.botos.telegram.runtime

import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.*

enum class AuthStep {
    STARTING, PARAMETERS, PHONE, EMAIL, EMAIL_CODE, CODE, PASSWORD, OTHER_DEVICE,
    REGISTRATION_REQUIRED, PREMIUM_REQUIRED, READY, LOGGING_OUT, CLOSING, CLOSED, UNSUPPORTED,
}
/** An app credential is not a user's bot token. It is injectable; never committed as a literal. */
class TelegramAppCredentials(val apiId: Int, internal val apiHash: String) {
    init { require(apiId > 0 && Regex("[0-9a-fA-F]{32}").matches(apiHash)) { "Invalid application configuration" } }
    override fun toString() = "TelegramAppCredentials([redacted])"
}
class TdParameters(
    internal val credentials: TelegramAppCredentials,
    internal val databaseDirectory: String,
    internal val filesDirectory: String,
    internal val language: String,
    internal val device: String,
    internal val systemVersion: String,
    internal val applicationVersion: String,
) {
    override fun toString() = "TdParameters([redacted])"
}

/** Only authoritative updateAuthorizationState changes the step. An Ok is NEVER login success. */
class Authorization(private val rpc: TdRpc) : AutoCloseable {
    private val _step = MutableStateFlow(AuthStep.STARTING)
    val step = _step.asStateFlow()
    private val operation = Mutex()
    private val subscription = rpc.observeUpdates { update ->
        if (update.type() == "updateAuthorizationState") {
            val type = (update["authorization_state"] as? JsonObject)?.type()
            _step.value = when (type) {
                "authorizationStateWaitTdlibParameters" -> AuthStep.PARAMETERS
                "authorizationStateWaitPhoneNumber" -> AuthStep.PHONE
                "authorizationStateWaitEmailAddress" -> AuthStep.EMAIL
                "authorizationStateWaitEmailCode" -> AuthStep.EMAIL_CODE
                "authorizationStateWaitCode" -> AuthStep.CODE
                "authorizationStateWaitPassword" -> AuthStep.PASSWORD
                "authorizationStateWaitOtherDeviceConfirmation" -> AuthStep.OTHER_DEVICE
                "authorizationStateWaitRegistration" -> AuthStep.REGISTRATION_REQUIRED
                "authorizationStateWaitPremiumPurchase" -> AuthStep.PREMIUM_REQUIRED
                "authorizationStateReady" -> AuthStep.READY
                "authorizationStateLoggingOut" -> AuthStep.LOGGING_OUT
                "authorizationStateClosing" -> AuthStep.CLOSING
                "authorizationStateClosed" -> AuthStep.CLOSED
                else -> AuthStep.UNSUPPORTED
            }
        }
    }

    suspend fun initialize(parameters: TdParameters, databaseKey: ByteArray) = perform(AuthStep.PARAMETERS) {
        if (databaseKey.size != 32) throw TdFailure(FailureKind.BAD_INPUT)
        val encoded = Base64.getEncoder().encodeToString(databaseKey)
        TdJson.command("setTdlibParameters") {
            put("use_test_dc", false)
            put("database_directory", parameters.databaseDirectory)
            put("files_directory", parameters.filesDirectory)
            put("database_encryption_key", encoded)
            put("use_file_database", true); put("use_chat_info_database", true)
            put("use_message_database", true); put("use_secret_chats", false)
            put("api_id", parameters.credentials.apiId); put("api_hash", parameters.credentials.apiHash)
            put("system_language_code", parameters.language); put("device_model", parameters.device)
            put("system_version", parameters.systemVersion); put("application_version", parameters.applicationVersion)
        }
    }
    suspend fun phone(value: String) = perform(AuthStep.PHONE) {
        if (!Regex("\\+[1-9][0-9]{5,14}").matches(value)) throw TdFailure(FailureKind.BAD_INPUT)
        TdJson.command("setAuthenticationPhoneNumber") { put("phone_number", value); put("settings", JsonNull) }
    }
    suspend fun code(value: String) = perform(AuthStep.CODE) {
        nonBlank(value, 128); TdJson.command("checkAuthenticationCode") { put("code", value) }
    }
    suspend fun password(value: String) = perform(AuthStep.PASSWORD) {
        nonBlank(value, 1024); TdJson.command("checkAuthenticationPassword") { put("password", value) }
    }
    suspend fun email(value: String) = perform(AuthStep.EMAIL) {
        nonBlank(value, 320)
        if ('@' !in value) throw TdFailure(FailureKind.BAD_INPUT)
        TdJson.command("setAuthenticationEmailAddress") { put("email_address", value) }
    }
    suspend fun emailCode(value: String) = perform(AuthStep.EMAIL_CODE) {
        nonBlank(value, 128)
        TdJson.command("checkAuthenticationEmailCode") {
            put("code", TdJson.command("emailAddressAuthenticationCode") { put("code", value) })
        }
    }

    /** Returns only after server logout is acknowledged AND native resources have closed. */
    suspend fun logOut() {
        perform(AuthStep.READY) { TdJson.command("logOut") }
        rpc.awaitClosed(60_000)
        if (_step.value != AuthStep.CLOSED) throw TdFailure(FailureKind.WRONG_STATE)
    }
    private suspend fun perform(expected: AuthStep, build: () -> JsonObject) {
        if (!operation.tryLock()) throw TdFailure(FailureKind.BUSY)
        try {
            if (_step.value != expected) throw TdFailure(FailureKind.WRONG_STATE)
            val result = rpc.request(build())
            if (result.type() != "ok") throw TdFailure(FailureKind.PROTOCOL)
        } finally { operation.unlock() }
    }
    private fun nonBlank(value: String, max: Int) {
        if (value.isBlank() || value.length > max || value.any { it == '\u0000' }) throw TdFailure(FailureKind.BAD_INPUT)
    }
    override fun close() { subscription.close() }
}
