package cloud.mallne.dicentra.aviator.plugin.synapse

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CatalystRequest(
    val parameters: Map<String, List<String>>,
    val body: JsonElement? = null
)
