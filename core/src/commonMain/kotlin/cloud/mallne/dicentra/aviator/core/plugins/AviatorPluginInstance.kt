package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

interface AviatorPluginInstance {
    val configurationBundle: AviatorPluginConfigScope
    val identity: String
    val x: PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any>
    fun requestReconfigure(oasConfig: JsonElement): AviatorPluginInstance = this
}