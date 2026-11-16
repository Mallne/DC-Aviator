package cloud.mallne.dicentra.aviator.core.engine

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.json.JsonElement

/** A definition's payload belongs to its delegation, not to the engine. */
interface ExecutableDefinition {
    val locator: ServiceLocator
    val delegation: String
    val pluginConfiguration: Map<String, JsonElement> get() = emptyMap()
    /** Raw OpenAPI server URLs; host policy must parse and validate before dispatch. */
    val outboundTargets: List<String> get() = emptyList()
}
