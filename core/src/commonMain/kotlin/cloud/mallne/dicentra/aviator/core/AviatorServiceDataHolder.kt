package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json

@Serializable
abstract class AviatorServiceDataHolder {
    abstract val serviceLocator: ServiceLocator
    abstract val plugins: List<AviatorPluginInstance>
    abstract val options: ServiceOptions
    abstract val route: Route
    abstract val oas: OpenAPI
    abstract val serializers: MutableList<StringFormat>

    val json: Json
        get() = serializers.find { it is Json } as? Json ?: Json
}