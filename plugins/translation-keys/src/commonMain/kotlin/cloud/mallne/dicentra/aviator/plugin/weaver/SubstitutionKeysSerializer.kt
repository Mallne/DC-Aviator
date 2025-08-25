package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import kotlinx.serialization.json.*
import kotlin.collections.component1
import kotlin.collections.component2

object SubstitutionKeysSerializer {

    fun flipTK(keys: Map<String, String>): Map<String, String> {
        return keys.map { (key, value) -> value to key }.toMap()
    }

    fun translateJson(translation: Map<String, String>, input: JsonElement, json: Json = Json): JsonElement {
        return when (input) {
            is JsonObject -> {
                json.encodeToJsonElement(
                    input.map { (key, value) -> (translation[key] ?: key) to translateJson(translation, value, json) }
                        .toMap()
                )
            }

            is JsonArray -> {
                json.encodeToJsonElement(
                    input.map { translateJson(translation, it, json) }
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

    fun translateForm(translation: Map<String, String>, input: List<Pair<String, String>>, json: Json = Json): List<Pair<String, String>> {
        return input.map { (key, value) -> (translation[key] ?: key) to value }
    }

    fun translate(translation: Map<String, String>, input: NetworkBody, json: Json = Json): NetworkBody {
        //change out the Object Keys with the tranlated Values
        return when (input) {
            is NetworkBody.Empty -> input
            is NetworkBody.Json -> NetworkBody.Json( translateJson(translation, input.json, json))
            is NetworkBody.Form -> NetworkBody.Form( translateForm(translation, input.formData, json))
        }
    }
}