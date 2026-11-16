package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.json.JsonElement

/**
 * Engine-owned plugin source. Definition activation can only select/reconfigure installed
 * plugins; it cannot introduce plugin code. Returned instances form one invocation snapshot.
 */
interface AviatorPluginRegistry {
    suspend fun invocationPlugins(
        locator: ServiceLocator,
        requested: Map<String, JsonElement>,
    ): AviatorPluginSnapshot

    object Empty : AviatorPluginRegistry {
        override suspend fun invocationPlugins(
            locator: ServiceLocator,
            requested: Map<String, JsonElement>,
        ) = AviatorPluginSnapshot()
    }
}

data class AviatorPluginSnapshot(
    val enginePlugins: List<EngineAviatorPluginInstance> = emptyList(),
    val delegationPlugins: List<DelegationAviatorPluginInstance> = emptyList(),
    val clientPlugins: List<AviatorPluginInstance> = emptyList(),
    val scopes: List<PluginScopeSnapshot> = emptyList(),
)
