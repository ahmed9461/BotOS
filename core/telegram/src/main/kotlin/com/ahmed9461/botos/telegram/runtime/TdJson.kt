package com.ahmed9461.botos.telegram.runtime

import kotlinx.serialization.json.*

/** Wire objects remain in the transport layer. Never log a request, response or parse exception. */
object TdJson {
    fun command(type: String, fields: JsonObjectBuilder.() -> Unit = {}): JsonObject = buildJsonObject {
        put("@type", type)
        fields()
    }
    fun parse(raw: String): JsonObject {
        if (raw.length > 8 * 1024 * 1024) throw TdFailure(FailureKind.PROTOCOL)
        return try { Json.parseToJsonElement(raw) as? JsonObject ?: throw TdFailure(FailureKind.PROTOCOL) }
        catch (_: Exception) { throw TdFailure(FailureKind.PROTOCOL) }
    }
}
fun JsonObject.type(): String = (get("@type") as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.string(name: String): String = (get(name) as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.number(name: String): Long? = (get(name) as? JsonPrimitive)?.longOrNull

enum class FailureKind { CLOSED, BUSY, PROTOCOL, NATIVE, REMOTE, WRONG_STATE, BAD_INPUT, NOT_CONFIGURED }
/** Deliberately has no raw payload, remote description, credentials, or nested exception. */
class TdFailure(val kind: FailureKind, val code: Int? = null) : Exception("Telegram operation: ${kind.name}")
