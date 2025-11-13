package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.model.ServiceLocator

interface AviatorPluginConfigScope {
    var serviceFilter: MutableList<ServiceLocator>
    val silentLoggingTags: MutableList<String>
}