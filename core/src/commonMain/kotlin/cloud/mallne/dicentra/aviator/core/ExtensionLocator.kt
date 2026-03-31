package cloud.mallne.dicentra.aviator.core

import io.ktor.openapi.*
import kotlinx.serialization.builtins.serializer

data class ExtensionLocator<X>(
    val key: String,
    val partOf: (X) -> Map<String, GenericElement>?,
) {
    fun find(part: X): String? {
        return findComplex(part)?.deserialize(String.serializer())
    }

    fun findComplex(part: X): GenericElement? {
        return partOf(part)?.get(key)
    }
}