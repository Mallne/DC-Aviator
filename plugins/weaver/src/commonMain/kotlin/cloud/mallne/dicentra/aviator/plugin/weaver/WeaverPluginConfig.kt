package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginConfigScope
import cloud.mallne.dicentra.aviator.core.plugins.PluginActivationStrategy
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.weaver.core.Weaver
import cloud.mallne.dicentra.weaver.core.specification.WeaverSchema

class WeaverPluginConfig : AviatorPluginConfigScope {
    override var serviceFilter: MutableList<ServiceLocator> = mutableListOf()
    override var activationStrategy: PluginActivationStrategy = PluginActivationStrategy.EnabledByDefault
    override val silentLoggingTags: MutableList<String> = mutableListOf()
    var weaver: Weaver = Weaver()
    var schema: WeaverSchema? = null
    var preserveDefaultOutput: Boolean = false
}