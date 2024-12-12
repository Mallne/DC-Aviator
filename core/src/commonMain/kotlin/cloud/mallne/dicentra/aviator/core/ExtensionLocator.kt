package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import kotlinx.serialization.json.JsonElement

data class ExtensionLocator<X : Extendable>(
    val key: String,
    val partOf: (X) -> Map<String, JsonElement>,
)