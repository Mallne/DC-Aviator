package cloud.mallne.dicentra.aviator.client.mock

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.RequestOptions
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionPipeline
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
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
    override val serializers: MutableList<StringFormat> = mutableListOf(Json)
) : AviatorServiceDataHolder() {

    constructor(
        serviceLocator: ServiceLocator,
        plugins: List<AviatorPluginInstance> = emptyList(),
        options: ServiceOptions,
        route: Route,
        oas: OpenAPI,
        json: Json = Json
    ): this(
        serviceLocator = serviceLocator,
        plugins = plugins,
        options = options,
        route = route,
        oas = oas,
        serializers = mutableListOf(json),
    )
    suspend inline fun request(
        options: RequestOptions = emptyMap(),
        requestParams: RequestParameters = RequestParameters()
    ): JsonElement? {
        return requestContextful(options, requestParams).result
    }

    suspend inline fun requestContextful(
        options: RequestOptions = emptyMap(),
        requestParams: RequestParameters = RequestParameters()
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