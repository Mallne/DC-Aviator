package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.model.ServiceLocator

interface APIToServiceConverter {
    fun build(api: OpenAPI, plugins: AviatorPluginActivationScope.() -> Unit = {}): Map<ServiceLocator, IAviatorService>
}