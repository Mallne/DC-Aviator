package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.StagedExecutor
import kotlinx.serialization.Serializable

interface AviatorPluginInstance :
    StagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any> {
    val configurationBundle: AviatorPluginConfigScope
    val identity: String
}