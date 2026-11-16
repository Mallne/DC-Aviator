package cloud.mallne.dicentra.aviator.plugin.adapter.xml

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.engine.ExecutableDefinition
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.adapter.request.RequestBodyAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.response.ResponseBodyAdapter
import cloud.mallne.dicentra.aviator.core.plugins.AviatorAdapterPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.DelegationInvocationContext
import cloud.mallne.dicentra.aviator.core.plugins.PluginScopeKey
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutorBuilder
import cloud.mallne.dicentra.aviator.core.plugins.PluginScopeSnapshot
import cloud.mallne.dicentra.aviator.core.plugins.TypedPluginScopeProvider
import cloud.mallne.dicentra.aviator.core.plugins.UniformAviatorPluginInstance
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.plugin.adapter.xml.request.XmlRequestAdapter
import cloud.mallne.dicentra.aviator.plugin.adapter.xml.response.XmlResponseAdapter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

internal data class XmlAdapterPluginInstance(
    override val configurationBundle: XmlAdapterPluginConfig,
) : AviatorAdapterPluginInstance, UniformAviatorPluginInstance {
    override val identity: String = XmlAdapter.identity
    override val key = PluginScopeKey<XmlAdapterPluginConfig>(identity)
    override val x: PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any> =
        PluginStagedExecutorBuilder.steps {}
    override suspend fun accepts(locator: ServiceLocator, definition: ExecutableDefinition): Boolean = true
    override suspend fun <I : Any, O : Any> execute(
        context: DelegationInvocationContext<I>,
        proceed: suspend () -> Result<O>,
    ): Result<O> = proceed()
    override suspend fun provide(
        locator: ServiceLocator,
        requested: Map<String, JsonElement>,
    ): PluginScopeSnapshot =
        TypedPluginScopeProvider(key) { _, _ -> configurationBundle }.provide(locator, requested)
    override val adapters: List<RequestBodyAdapter<out NetworkBody>> = listOf(XmlRequestAdapter)
    override val deserializers: List<ResponseBodyAdapter> = listOf(XmlResponseAdapter)
}
