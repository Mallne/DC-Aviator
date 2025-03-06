package cloud.mallne.dicentra.weaver.plugins.interception

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance

object InterceptionPlugin : AviatorPlugin<InterceptionPluginConfig> {
    override val identity: String = "DC-AV-Interception"
    override fun install(config: InterceptionPluginConfig.() -> Unit): AviatorPluginInstance {
        val pluginConfig = InterceptionPluginConfig()
        config.invoke(pluginConfig)
        return InterceptionPluginInstance(
            configurationBundle = pluginConfig,
            identity =  identity,
            x = pluginConfig.pipeline
        )
    }
}