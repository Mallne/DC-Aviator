package cloud.mallne.dicentra.aviator.client.fetch

import cloud.mallne.dicentra.aviator.core.*
import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder.Companion.json
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionPipeline
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.adapter.request.RequestBodyAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.response.ResponseBodyAdapter
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import io.ktor.openapi.OpenApiDoc
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.typeOf

/**
 * An Aviator operation configured to send requests with the JavaScript Fetch API.
 *
 * Instances are normally created by [FetchAviatorServiceConverter]. Use [request] for the
 * deserialized response, or [requestContextful] when request/response chain details are also
 * needed for diagnostics.
 *
 * @property fetcher Fetch-compatible JavaScript function used for this service's requests.
 *   It may be a framework-provided function such as SvelteKit's `event.fetch`.
 * @property maxResponseBytes maximum accepted response body size, in bytes.
 */
data class FetchAviatorService(
    override val serviceLocator: ServiceLocator,
    override val options: ServiceOptions,
    override val plugins: List<AviatorPluginInstance>,
    override val route: Route,
    override val oas: OpenApiDoc,
    override val adapters: List<RequestBodyAdapter<out NetworkBody>>,
    override val deserializers: List<ResponseBodyAdapter>,
    /** Fetch-compatible function; defaults to `globalThis.fetch`. */
    val fetcher: dynamic = defaultFetch(),
    override val serializers: List<SerialFormat> = listOf(Json),
    /** Maximum response size accepted from Fetch, in bytes (defaults to 16 MiB). */
    val maxResponseBytes: Int = 16_777_216,
) : AviatorServiceDataHolder {
    init {
        require(maxResponseBytes > 0) { "maxResponseBytes must be positive" }
        AviatorServiceUtils.validate(this)
    }

    inline fun <reified T : InflatedServiceOptions> optionBundle(): T =
        AviatorServiceUtils.optionBundle(options, json)

    /**
     * Executes the operation and returns its full execution context.
     *
     * The context includes the final result and network chain. A non-success HTTP response or
     * an unhandled response format leaves [FetchExecutionContext.result] null; inspect the
     * network chain for status and response details.
     *
     * @param requestBody optional serializable request body. Omit it for bodyless operations.
     * @param useSerializer serializer for the expected response type.
     * @param options per-request Aviator options passed through the execution pipeline.
     * @param requestParams path, query, and header parameter values for this operation.
     */
    suspend inline fun <reified O : @Serializable Any, reified B : @Serializable Any> requestContextful(
        requestBody: B? = null,
        useSerializer: KSerializer<O> = serializer<O>(),
        options: RequestOptions = emptyMap(),
        requestParams: RequestParameters = RequestParameters(),
    ): FetchExecutionContext<O, B> {
        val context = FetchExecutionContext(
            dataHolder = this,
            outputClazz = AviatorServiceUtils.makeClazzDefinition<O>(),
            body = requestBody,
            bodyClazz = if (requestBody != null) Triple(B::class, typeOf<B>(), serializer<B>()) else null,
            options = options.toMutableMap(),
            requestParams = requestParams,
        )
        return AviatorExecutionPipeline(context, plugins, FetchStagedExecutor()).run()
    }

    /**
     * Executes the operation and returns its deserialized response, or `null` if no successful
     * response can be deserialized.
     *
     * @param requestBody optional serializable request body.
     * @param useSerializer serializer for the expected response type.
     * @param options per-request Aviator options passed through the execution pipeline.
     * @param requestParams path, query, and header parameter values for this operation.
     */
    suspend inline fun <reified O : @Serializable Any, reified B : @Serializable Any> request(
        requestBody: B? = null,
        useSerializer: KSerializer<O> = serializer<O>(),
        options: RequestOptions = emptyMap(),
        requestParams: RequestParameters = RequestParameters(),
    ): O? = requestContextful<O, B>(requestBody, useSerializer, options, requestParams).result

    /** Executes a bodyless operation and returns its deserialized response, or `null`. */
    suspend inline fun <reified O : @Serializable Any> request(
        useSerializer: KSerializer<O> = serializer<O>(),
        options: RequestOptions = emptyMap(),
        requestParams: RequestParameters = RequestParameters(),
    ): O? = request<O, NoBody>(null, useSerializer, options, requestParams)
}
