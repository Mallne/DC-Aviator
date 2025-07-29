package cloud.mallne.dicentra.aviator.plugins.weaver

import kotlinx.serialization.json.*

object SubstitutionKeysSerializer {

    fun flipTK(keys: Map<String, String>): Map<String, String> {
        return keys.map { (key, value) -> value to key }.toMap()
    }

    fun translate(translation: Map<String, String>, input: JsonElement, json: Json = Json): JsonElement {
        //change out the Object Keys with the tranlated Values
        return when (input) {
            is JsonObject -> {
                json.encodeToJsonElement(
                    input.map { (key, value) -> (translation[key] ?: key) to translate(translation, value, json) }
                        .toMap()
                )
            }

            is JsonArray -> {
                json.encodeToJsonElement(
                    input.map { translate(translation, it, json) }
                )
            }

            is JsonPrimitive -> {
                input
            }

            is JsonNull -> {
                input
            }
        }
    }
}