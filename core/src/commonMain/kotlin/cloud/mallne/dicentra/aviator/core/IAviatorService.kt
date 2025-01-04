package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.ServiceLocator

interface IAviatorService {
    val serviceLocator: ServiceLocator
    val plugins: List<AviatorPluginInstance>
    val options: ServiceOptions
    val route: Route
    val oas: OpenAPI
}