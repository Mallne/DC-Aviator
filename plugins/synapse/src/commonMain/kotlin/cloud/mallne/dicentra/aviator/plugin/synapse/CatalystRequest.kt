package cloud.mallne.dicentra.aviator.plugin.synapse

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

@Serializable
data class CatalystRequest(
    /** Catalyst request envelope version. The initial contract uses typed parameters. */
    val version: Int = 1,
    val body: JsonElement? = null,
    /** Values are paired with cardinality and an optional serializer codec id. */
    val typedParameters: Map<String, CatalystParameterValue> = emptyMap(),
    /** Optional original service identities to query; when non-empty this overrides automatic filters. */
    val identities: List<String> = emptyList(),
)

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
