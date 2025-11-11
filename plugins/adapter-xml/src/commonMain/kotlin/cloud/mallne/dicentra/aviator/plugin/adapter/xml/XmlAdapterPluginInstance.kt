package cloud.mallne.dicentra.aviator.plugin.adapter.xml

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.adapter.request.RequestBodyAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.response.ResponseBodyAdapter
import cloud.mallne.dicentra.aviator.core.plugins.AviatorAdapterPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutorBuilder
import cloud.mallne.dicentra.aviator.plugin.adapter.xml.request.XmlRequestAdapter
import cloud.mallne.dicentra.aviator.plugin.adapter.xml.response.XmlResponseAdapter
import kotlinx.serialization.Serializable

internal data class XmlAdapterPluginInstance(
    override val configurationBundle: XmlAdapterPluginConfig,
) : AviatorAdapterPluginInstance {
    override val identity: String = XmlAdapter.identity
    override val x: PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any> =
        PluginStagedExecutorBuilder.steps {}
    override val adapters: List<RequestBodyAdapter<out NetworkBody>> = listOf(XmlRequestAdapter)
    override val deserializers: List<ResponseBodyAdapter> = listOf(XmlResponseAdapter)
}