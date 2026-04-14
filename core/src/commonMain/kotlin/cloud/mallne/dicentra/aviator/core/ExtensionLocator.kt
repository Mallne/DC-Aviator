package cloud.mallne.dicentra.aviator.core

import io.ktor.openapi.*
import kotlinx.serialization.builtins.serializer

data class ExtensionLocator<X>(
    val key: String,
    val partOf: (X) -> Map<String, GenericElement>?,
) {
    fun find(part: X): String? {
        val ge = findComplex(part) ?: return null
        return try {
            ge.takeIf { it.isString() }?.deserialize(String.serializer())
        } catch (e: Exception) {
            null
        }
    }

    fun findComplex(part: X): GenericElement? {
        return partOf(part)?.get(key)
    }
}