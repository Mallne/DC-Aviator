package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.InflatedServiceOptions
import cloud.mallne.dicentra.aviator.core.RequestOptions
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionPipeline
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import io.ktor.client.HttpClient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.typeOf

data class KtorAviatorService(
    override val serviceLocator: ServiceLocator,
    override val options: ServiceOptions,
    val client: HttpClient,
    override val plugins: List<AviatorPluginInstance>,
    override val route: Route,
    override val oas: OpenAPI,
    override val json: Json
) : AviatorServiceDataHolder() {

    init {
        AviatorServiceUtils.validate(this)
    }

    inline fun <reified T : InflatedServiceOptions> optionBundle(): T = AviatorServiceUtils.optionBundle(options, json)

    suspend inline fun <reified O : @Serializable Any, reified B : @Serializable Any> requestContextful(
        requestBody: B? = null,
        useSerializer: KSerializer<O> = serializer<O>(),
        options: RequestOptions = emptyMap(),
        requestParams: RequestParameters = RequestParameters()
    ): KtorExecutionContext<O, B> {
        val executor = KtorStagedExecutor<O, B>()
        val pipeline = AviatorExecutionPipeline(
            context = KtorExecutionContext(
                dataHolder = this,
                outputClazz = AviatorServiceUtils.makeClazzDefinition(),
                body = requestBody,
                bodyClazz = if (requestBody != null) {
                    Triple(B::class, typeOf<B>(), serializer<B>())
                } else {
                    null
                },
                options = options.toMutableMap(),
                requestParams = requestParams,
            ),
            plugins = plugins,
            executor = executor,
        )

        val ctx = pipeline.run()
        return ctx
    }

    suspend inline fun <reified O : @Serializable Any, reified B : @Serializable Any> request(
        requestBody: B? = null,
        useSerializer: KSerializer<O> = serializer<O>(),
        options: RequestOptions = emptyMap(),
        requestParams: RequestParameters = RequestParameters()
    ): O? = requestContextful<O, B>(requestBody, useSerializer, options, requestParams).result

    fun close() = client.close()

    internal fun catchPaths(requestParams: RequestParameters = RequestParameters()): List<String> {
        return AviatorServiceUtils.catchPaths(this, requestParams)
    }
}