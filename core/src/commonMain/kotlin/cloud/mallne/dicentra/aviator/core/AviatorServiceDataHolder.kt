package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.json.Json

interface AviatorServiceDataHolder {
    val serviceLocator: ServiceLocator
    val plugins: List<AviatorPluginInstance>
    val options: ServiceOptions
    val route: Route
    val oas: OpenAPI
    val json: Json
}