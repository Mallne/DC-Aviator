package cloud.mallne.dicentra.aviator.core.execution.logging

import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutorBuilder

object LoggingPlugin : AviatorPlugin<LoggingPluginConfig> {
    override val identity: String = "DC-AV-CORE-Logging"

    @OptIn(InternalAviatorAPI::class)
    override fun install(config: LoggingPluginConfig.() -> Unit): AviatorPluginInstance {
        val pluginConfig = LoggingPluginConfig()
        config.invoke(pluginConfig)
        return LoggingPluginInstance(
            configurationBundle = pluginConfig,
            identity = identity,
            x = PluginStagedExecutorBuilder.steps {
                before(AviatorExecutionStages.Invocation) {
                    it.logger = pluginConfig.logger
                }
            }
        )
    }
}