package cloud.mallne.dicentra.aviator.plugin.synapse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Catalyst-only encoder for final values produced by host Aviator plugins. */
interface CatalystResultCodec {
    val identity: String
    fun supports(value: Any): Boolean
    fun encode(value: Any, json: Json): JsonElement
}

object CatalystResultEncoder {
    fun encode(value: Any, codecs: Iterable<CatalystResultCodec>, json: Json = Json): JsonElement {
        if (value is JsonElement) return value

        val matches = codecs.distinctBy { it.identity }.filter { it.supports(value) }
        if (matches.size > 1) throw CatalystResultSerializationException()
        matches.singleOrNull()?.let { return it.encode(value, json) }
        return encodeJsonValue(value) ?: throw CatalystResultSerializationException()
    }

    private fun encodeJsonValue(value: Any): JsonElement? {
        return when (value) {
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Iterable<*> -> {
                val elements = mutableListOf<JsonElement>()
                for (item in value) {
                    if (item == null) elements += JsonNull
                    else elements += encodeJsonValue(item) ?: return null
                }
                JsonArray(elements)
            }
            is Map<*, *> -> {
                val members = mutableMapOf<String, JsonElement>()
                for ((key, item) in value) {
                    if (key !is String) return null
                    members[key] = if (item == null) JsonNull else encodeJsonValue(item) ?: return null
                }
                JsonObject(members)
            }
            else -> null
        }
    }
}

class CatalystResultSerializationException : RuntimeException()
