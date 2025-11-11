package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.adapter.request.RequestBodyAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.response.ResponseBodyAdapter

interface AviatorAdapterPluginInstance : AviatorPluginInstance {
    val adapters: List<RequestBodyAdapter<out NetworkBody>>
    val deserializers: List<ResponseBodyAdapter>
}