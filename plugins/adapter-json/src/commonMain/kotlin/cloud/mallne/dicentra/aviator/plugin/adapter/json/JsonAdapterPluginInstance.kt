package cloud.mallne.dicentra.aviator.plugin.adapter.json

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
import cloud.mallne.dicentra.aviator.plugin.adapter.json.request.JsonRequestAdapter
import cloud.mallne.dicentra.aviator.plugin.adapter.json.response.JsonResponseAdapter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

internal data class JsonAdapterPluginInstance(
    override val configurationBundle: JsonAdapterPluginConfig,
) : AviatorAdapterPluginInstance, UniformAviatorPluginInstance {
    override val identity: String = JsonAdapter.identity
    override val key = PluginScopeKey<JsonAdapterPluginConfig>(identity)
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
    override val adapters: List<RequestBodyAdapter<out NetworkBody>> = listOf(JsonRequestAdapter)
    override val deserializers: List<ResponseBodyAdapter> = listOf(JsonResponseAdapter)
}
