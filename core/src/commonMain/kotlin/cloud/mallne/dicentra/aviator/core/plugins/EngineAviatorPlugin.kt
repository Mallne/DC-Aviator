package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.core.engine.ExecutableDefinition
import cloud.mallne.dicentra.aviator.model.ServiceLocator

/** Install-time plugin definition; installation bakes configuration into an immutable instance. */
interface EngineAviatorPlugin<ConfigScope : AviatorPluginConfigScope> {
    val identity: String
    fun install(config: ConfigScope.() -> Unit): EngineAviatorPluginInstance
}

/** Narrows coordinator matches without adding or replacing definitions. */
interface EngineAviatorPluginInstance {
    val identity: String
    val configurationBundle: AviatorPluginConfigScope
    fun requestReconfigure(oasConfig: kotlinx.serialization.json.JsonElement): EngineAviatorPluginInstance = this
    suspend fun accepts(locator: ServiceLocator, definition: ExecutableDefinition): Boolean
}
