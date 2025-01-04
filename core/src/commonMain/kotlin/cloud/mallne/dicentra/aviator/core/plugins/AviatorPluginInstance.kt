package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.core.execution.StagedExecutor

interface AviatorPluginInstance : StagedExecutor {
    val configurationBundle: AviatorPluginConfigScope
    val identity: String
}