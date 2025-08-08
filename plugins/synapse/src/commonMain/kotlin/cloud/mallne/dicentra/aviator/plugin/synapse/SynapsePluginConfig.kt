package cloud.mallne.dicentra.aviator.plugin.synapse

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginConfigScope
import cloud.mallne.dicentra.aviator.core.plugins.PluginActivationStrategy
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.Serializable

@Serializable
data class SynapsePluginConfig(val active: Boolean = false) : AviatorPluginConfigScope {
    override var serviceFilter: MutableList<ServiceLocator> = mutableListOf()
    override var activationStrategy: PluginActivationStrategy = PluginActivationStrategy.EnabledByOAS
    override val silentLoggingTags: MutableList<String> = mutableListOf()
}