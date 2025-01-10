package cloud.mallne.dicentra.aviator.core.mock

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.RequestOptions
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionPipeline
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class MockedAviatorService(
    override val serviceLocator: ServiceLocator,
    override val plugins: List<AviatorPluginInstance> = emptyList(),
    override val options: ServiceOptions,
    override val route: Route,
    override val oas: OpenAPI,
    @Transient
    override val json: Json = Json
) : AviatorServiceDataHolder() {
    suspend inline fun request(
        options: RequestOptions = emptyMap(),
        requestParams: Map<String, List<String>> = emptyMap()
    ): JsonElement? {
        return requestContextful(options, requestParams).result
    }

    suspend inline fun requestContextful(
        options: RequestOptions = emptyMap(),
        requestParams: Map<String, List<String>> = emptyMap()
    ): MockExecutionContext {

        val executor = MockedStagedExecutor()
        val pipeline = AviatorExecutionPipeline(
            context = MockExecutionContext(
                dataHolder = this,
                options = options.toMutableMap(),
                requestParams = requestParams,
            ),
            plugins = plugins,
            executor = executor,
        )

        val ctx = pipeline.run()
        return ctx
    }
}