package cloud.mallne.dicentra.aviator.koas.extensions

import kotlinx.serialization.json.JsonElement

interface Extendable {
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [kotlinx.serialization.json.JsonNull],
     * [kotlinx.serialization.json.JsonPrimitive], [kotlinx.serialization.json.JsonArray] or [kotlinx.serialization.json.JsonObject].
     */
    var extensions: Map<String, JsonElement>
}