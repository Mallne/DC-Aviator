package cloud.mallne.dicentra.aviator.plugin.adapter.json

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginConfigScope
import cloud.mallne.dicentra.aviator.core.plugins.PluginActivationStrategy
import cloud.mallne.dicentra.aviator.model.ServiceLocator

class JsonAdapterPluginConfig() : AviatorPluginConfigScope {
    override var serviceFilter: MutableList<ServiceLocator> = mutableListOf()
    override var activationStrategy: PluginActivationStrategy = PluginActivationStrategy.EnabledByDefault
    override val silentLoggingTags: MutableList<String> = mutableListOf()
}