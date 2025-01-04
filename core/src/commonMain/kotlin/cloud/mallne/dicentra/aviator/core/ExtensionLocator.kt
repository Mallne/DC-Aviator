package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ExtensionLocator<X : Extendable>(
    val key: String,
    val partOf: (X) -> Map<String, JsonElement>,
) {
    fun find(part: X): String? {
        return findComplex(part)?.jsonPrimitive?.contentOrNull
    }

    fun findComplex(part: X): JsonElement? {
        return partOf(part)[key]
    }
}