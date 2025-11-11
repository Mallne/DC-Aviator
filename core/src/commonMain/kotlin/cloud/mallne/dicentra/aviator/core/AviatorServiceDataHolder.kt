package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.adapter.CommonAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.request.RequestBodyAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.response.ResponseBodyAdapter
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.json.Json

interface AviatorServiceDataHolder {
    val serviceLocator: ServiceLocator
    val plugins: List<AviatorPluginInstance>
    val adapters: List<RequestBodyAdapter<out NetworkBody>>
        get() = CommonAdapter.adapters
    val deserializers: List<ResponseBodyAdapter>
        get() = CommonAdapter.deserializers
    val options: ServiceOptions
    val route: Route
    val oas: OpenAPI
    val serializers: List<SerialFormat>

    companion object {
        val <T : AviatorServiceDataHolder> T.json: Json
            get() = serializers.find { it is Json } as? Json ?: Json
    }
}