package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder.Companion.json
import cloud.mallne.dicentra.aviator.core.InflatedServiceOptions
import cloud.mallne.dicentra.aviator.core.NoBody
import cloud.mallne.dicentra.aviator.core.RequestOptions
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionPipeline
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.adapter.request.RequestBodyAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.response.ResponseBodyAdapter
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import io.ktor.client.*
import io.ktor.openapi.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.serialization.serializer

data class KtorAviatorService(
    override val serviceLocator: ServiceLocator,
    override val options: ServiceOptions,
    val client: HttpClient,
    override val plugins: List<AviatorPluginInstance>,
    override val route: Route,
    override val oas: OpenApiDoc,
    override val adapters: List<RequestBodyAdapter<out NetworkBody>>,
    override val deserializers: List<ResponseBodyAdapter>,
    override val serializers: List<SerialFormat> = listOf(Json),
    val maxResponseBytes: Long = 16_777_216,
) : AviatorServiceDataHolder {

    init {
        AviatorServiceUtils.validate(this)
    }

    inline fun <reified T : InflatedServiceOptions> optionBundle(): T = AviatorServiceUtils.optionBundle(options, json)

    suspend fun <O : @Serializable Any> requestContextful(
        outputClazz: Triple<KClass<O>, KType, KSerializer<O>>,
        requestBody: JsonElement? = null,
        options: RequestOptions = emptyMap(),
        requestParams: RequestParameters = RequestParameters(),
    ): KtorExecutionContext<O, JsonElement> {
        val context = KtorExecutionContext(
            dataHolder = this,
            outputClazz = outputClazz,
            body = requestBody,
            bodyClazz = if (requestBody != null) {
                Triple(JsonElement::class, typeOf<JsonElement>(), serializer<JsonElement>())
            } else {
                null
            },
            options = options.toMutableMap(),
            requestParams = requestParams,
        )
        return AviatorExecutionPipeline(context, plugins, KtorStagedExecutor<O, JsonElement>()).run()
    }

    suspend fun <O : @Serializable Any, B : @Serializable Any> requestContextful(
        outputClazz: Triple<KClass<O>, KType, KSerializer<O>>,
        bodyClazz: Triple<KClass<B>, KType, KSerializer<B>>,
        requestBody: B?,
        options: RequestOptions = emptyMap(),
        requestParams: RequestParameters = RequestParameters(),
    ): KtorExecutionContext<O, B> {
        val context = KtorExecutionContext(
            dataHolder = this,
            outputClazz = outputClazz,
            body = requestBody,
            bodyClazz = bodyClazz.takeIf { requestBody != null },
            options = options.toMutableMap(),
            requestParams = requestParams,
        )
        return AviatorExecutionPipeline(context, plugins, KtorStagedExecutor<O, B>()).run()
    }

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

    suspend inline fun <reified O : @Serializable Any> request(
        useSerializer: KSerializer<O> = serializer<O>(),
        options: RequestOptions = emptyMap(),
        requestParams: RequestParameters = RequestParameters()
    ): O? = request<O, NoBody>(null, useSerializer, options, requestParams)

    fun close() = client.close()

    internal fun catchPaths(requestParams: RequestParameters = RequestParameters()): List<String> {
        return AviatorServiceUtils.catchPaths(this, requestParams)
    }
}
