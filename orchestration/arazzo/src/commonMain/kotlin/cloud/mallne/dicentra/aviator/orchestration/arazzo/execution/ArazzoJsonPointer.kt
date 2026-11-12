package cloud.mallne.dicentra.aviator.orchestration.arazzo.execution

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Looks up an RFC 6901 JSON Pointer, returning null only when the target is missing. */
internal fun JsonElement.atJsonPointer(pointer: String): JsonElement? {
    require(pointer.isEmpty() || pointer.startsWith('/')) { "JSON Pointer must be empty or start with '/'" }
    if (pointer.isEmpty()) return this

    var current: JsonElement = this
    for (rawToken in pointer.drop(1).split('/')) {
        val token = decodePointerToken(rawToken)
        current = when (val value = current) {
            is JsonObject -> value[token] ?: return null
            is JsonArray -> {
                if (token.isEmpty() || (token.length > 1 && token.startsWith('0')) || token.any { !it.isDigit() }) return null
                val index = token.toIntOrNull() ?: return null
                value.getOrNull(index) ?: return null
            }
            else -> return null
        }
    }
    return current
}

private fun decodePointerToken(token: String): String {
    val result = StringBuilder(token.length)
    var index = 0
    while (index < token.length) {
        val char = token[index]
        if (char != '~') {
            result.append(char)
            index++
        } else {
            require(index + 1 < token.length) { "Invalid JSON Pointer escape" }
            result.append(when (token[index + 1]) {
                '0' -> '~'
                '1' -> '/'
                else -> throw IllegalArgumentException("Invalid JSON Pointer escape")
            })
            index += 2
        }
    }
    return result.toString()
}

internal fun isValidJsonPointerSyntax(pointer: String): Boolean = try {
    require(pointer.isEmpty() || pointer.startsWith('/'))
    if (pointer.isNotEmpty()) pointer.drop(1).split('/').forEach(::decodePointerToken)
    true
} catch (_: IllegalArgumentException) {
    false
}
