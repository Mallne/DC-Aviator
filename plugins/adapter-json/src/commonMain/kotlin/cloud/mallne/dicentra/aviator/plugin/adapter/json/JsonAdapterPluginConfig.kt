package cloud.mallne.dicentra.aviator.plugin.adapter.json

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginConfigScope
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginDsl
import cloud.mallne.dicentra.aviator.model.ServiceLocator

@AviatorPluginDsl
class JsonAdapterPluginConfig : AviatorPluginConfigScope {
    override var serviceFilter: MutableList<ServiceLocator> = mutableListOf()
    override val silentLoggingTags: MutableList<String> = mutableListOf()
}