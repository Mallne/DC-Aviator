package cloud.mallne.dicentra.aviator.core.mock

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

@Serializable
data class MockedAviatorService(
    override val serviceLocator: ServiceLocator,
    override val plugins: List<AviatorPluginInstance>,
    override val options: ServiceOptions,
    override val route: Route,
    override val oas: OpenAPI,
    @Transient
    override val json: Json = Json

) : AviatorServiceDataHolder