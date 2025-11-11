package cloud.mallne.dicentra.aviator.plugin.adapter.json

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.adapter.request.RequestBodyAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.response.ResponseBodyAdapter
import cloud.mallne.dicentra.aviator.core.plugins.AviatorAdapterPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutorBuilder
import cloud.mallne.dicentra.aviator.plugin.adapter.json.request.JsonRequestAdapter
import cloud.mallne.dicentra.aviator.plugin.adapter.json.response.JsonResponseAdapter
import kotlinx.serialization.Serializable

internal data class JsonAdapterPluginInstance(
    override val configurationBundle: JsonAdapterPluginConfig,
) : AviatorAdapterPluginInstance {
    override val identity: String = JsonAdapter.identity
    override val x: PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any> =
        PluginStagedExecutorBuilder.steps {}
    override val adapters: List<RequestBodyAdapter<out NetworkBody>> = listOf(JsonRequestAdapter)
    override val deserializers: List<ResponseBodyAdapter> = listOf(JsonResponseAdapter)
}