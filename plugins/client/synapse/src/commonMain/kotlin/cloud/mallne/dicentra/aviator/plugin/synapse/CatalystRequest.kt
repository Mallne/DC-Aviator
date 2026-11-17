package cloud.mallne.dicentra.aviator.plugin.synapse

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class CatalystRequest(
    /** Catalyst request envelope version. The initial contract uses typed parameters. */
    val version: Int = 1,
    val body: JsonElement? = null,
    /** Values are paired with cardinality and an optional serializer codec id. */
    val typedParameters: Map<String, CatalystParameterValue> = emptyMap(),
    /** Optional original service identities to query; when non-empty this overrides automatic filters. */
    val identities: List<String> = emptyList(),
    /** JSON by default. Serialized bodies carry their base64 string in body, not a second payload. */
    val bodyFormat: CatalystBodyFormat = CatalystBodyFormat.Json,
)

/** How to interpret body. Supported upstream media types are validated against the original action. */
@Serializable
sealed interface CatalystBodyFormat {
    @Serializable
    @SerialName("json")
    data object Json : CatalystBodyFormat

    /** body is a canonical standard padded base64 JSON string containing already serialized bytes. */
    @Serializable
    @SerialName("base64")
    data class Base64(val contentType: String) : CatalystBodyFormat
}

@Serializable
data class CatalystParameterValue(
    val cardinality: Cardinality,
    val values: List<JsonElement>,
    /** kotlinx.serialization descriptor serial name; null means a raw JSON value. */
    val codec: String? = null,
) {
    @Serializable
    enum class Cardinality { SINGLE, MULTI }
}
