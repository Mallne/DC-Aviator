package cloud.mallne.dicentra.aviator.core.model.oas

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

interface Extendable {
    var extensions: MutableMap<String, JsonElement>
}