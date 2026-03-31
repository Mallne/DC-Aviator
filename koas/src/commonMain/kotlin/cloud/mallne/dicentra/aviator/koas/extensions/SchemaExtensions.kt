package cloud.mallne.dicentra.aviator.koas.extensions

import io.ktor.openapi.*
import kotlinx.serialization.builtins.serializer

object SchemaExtensions {
    val JsonSchema.stringEnums: List<String?>?
        get() = enum?.map { it?.deserialize(String.serializer()) }
}