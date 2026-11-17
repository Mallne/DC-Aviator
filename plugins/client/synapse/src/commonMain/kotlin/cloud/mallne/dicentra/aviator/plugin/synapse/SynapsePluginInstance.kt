package cloud.mallne.dicentra.aviator.plugin.synapse

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

data class SynapsePluginInstance(
    override val configurationBundle: SynapsePluginConfig,
    override val identity: String,
    override val x: PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any>
) : AviatorPluginInstance {
    override fun requestReconfigure(oasConfig: JsonElement): AviatorPluginInstance {
        try {
            val newConf = Json.decodeFromJsonElement(SynapsePluginConfig.serializer(), oasConfig)
            return this.copy(configurationBundle = newConf)
        } catch (_: IllegalArgumentException) {
            return this
        }
    }
}