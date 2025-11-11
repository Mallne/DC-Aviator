package cloud.mallne.dicentra.aviator.plugin.adapter.xml

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginConfigScope
import cloud.mallne.dicentra.aviator.core.plugins.PluginActivationStrategy
import cloud.mallne.dicentra.aviator.model.ServiceLocator

class XmlAdapterPluginConfig() : AviatorPluginConfigScope {
    override var serviceFilter: MutableList<ServiceLocator> = mutableListOf()
    override var activationStrategy: PluginActivationStrategy = PluginActivationStrategy.EnabledByDefault
    override val silentLoggingTags: MutableList<String> = mutableListOf()
}