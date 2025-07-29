package cloud.mallne.dicentra.aviator.plugins.weaver

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor
import kotlinx.serialization.Serializable

class TKPInstance(
    override val configurationBundle: TranslationKeysPluginConfig,
    override val identity: String,
    override val x: PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any>
) : AviatorPluginInstance