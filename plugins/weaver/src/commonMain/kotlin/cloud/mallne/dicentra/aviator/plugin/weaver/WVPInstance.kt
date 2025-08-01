package cloud.mallne.dicentra.aviator.plugins.weaver

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor
import cloud.mallne.dicentra.aviator.plugin.weaver.WeaverPluginConfig
import cloud.mallne.dicentra.weaver.core.execution.WeaverEngine
import kotlinx.serialization.Serializable

class WVPInstance(
    override val configurationBundle: WeaverPluginConfig,
    override val identity: String,
    override val x: PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any>
) : AviatorPluginInstance {
    var engine: WeaverEngine? = null
}