package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.model.ServiceLocator

@AviatorPluginDsl
interface AviatorPluginConfigScope {
    var serviceFilter: MutableList<ServiceLocator>
    val silentLoggingTags: MutableList<String>
}