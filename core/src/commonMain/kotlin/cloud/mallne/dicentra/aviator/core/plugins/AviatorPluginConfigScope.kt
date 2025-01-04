package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.model.ServiceLocator

interface AviatorPluginConfigScope {
    val serviceFilter: List<ServiceLocator>
}