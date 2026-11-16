package cloud.mallne.dicentra.aviator.client.fetch

import cloud.mallne.dicentra.aviator.core.engine.EngineTemplate
import cloud.mallne.dicentra.aviator.core.plugins.EngineInvocationContext
import cloud.mallne.dicentra.aviator.core.engine.OpenApiClientInput
import cloud.mallne.dicentra.aviator.core.engine.EngineDefinitionConverter
import cloud.mallne.dicentra.aviator.core.engine.ExecutableDefinition
import cloud.mallne.dicentra.aviator.core.engine.OpenApiOperationDefinitionRequest
import cloud.mallne.dicentra.aviator.core.NoBody
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-pluginMaterialization`
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType

class FetchClientDefinition(
    val service: FetchAviatorService,
    pluginConfiguration: Map<String, JsonElement> =
        service.route.`x-dicentra-aviator-pluginMaterialization`.orEmpty(),
) : ExecutableDefinition {
    override val pluginConfiguration: Map<String, JsonElement> = pluginConfiguration.toMap()
    override val locator: ServiceLocator get() = service.serviceLocator
    override val delegation: String get() = FetchEngineTemplate.IDENTITY
    override val outboundTargets: List<String> get() = service.oas.servers.orEmpty().map { it.url }
}

class FetchEngineDefinitionConverter(
    private val converter: FetchAviatorServiceConverter,
) : EngineDefinitionConverter<OpenApiOperationDefinitionRequest, FetchClientDefinition> {
    override fun convert(source: OpenApiOperationDefinitionRequest): FetchClientDefinition =
        FetchClientDefinition(
            converter.buildOperation(source.api, source.route, source.locator, source.activatePlugins),
        )
}

class FetchClientInvocationException(
    val statusCode: Int?,
    message: String,
) : Exception(message)

class FetchEngineTemplate<O : @Serializable Any, B : @Serializable Any>(
    private val outputClazz: Triple<KClass<O>, KType, KSerializer<O>>,
    private val bodyClazz: Triple<KClass<B>, KType, KSerializer<B>>,
) : EngineTemplate<OpenApiClientInput<B>, FetchExecutionContext<O, B>> {
    override val identity: String = IDENTITY

    override suspend fun execute(
        definition: ExecutableDefinition,
        input: OpenApiClientInput<B>,
        context: EngineInvocationContext,
    ): Result<FetchExecutionContext<O, B>> {
        require(definition is FetchClientDefinition) { "Expected a Fetch client definition" }
        val plugins = context.clientPluginsOver(definition.service.plugins)
        val service = definition.service.copy(
            plugins = plugins.plugins,
            adapters = plugins.adapters,
            deserializers = plugins.deserializers,
        )
        val execution = service.requestContextful(
            outputClazz = outputClazz,
            bodyClazz = bodyClazz,
            requestBody = input.body,
            options = input.options,
            requestParams = input.parameters,
        )
        return Result.success(execution)
    }

    override fun classifyFailure(output: FetchExecutionContext<O, B>): Exception? {
        val response = output.networkChain.firstOrNull { it.response?.status?.value in 200..299 }?.response
        if (response == null) {
            val status = output.networkChain.firstNotNullOfOrNull { it.response?.status?.value }
            return FetchClientInvocationException(status, "No successful client response")
        }
        if (response.content?.isNotEmpty() == true && output.result == null) {
            return FetchClientInvocationException(response.status.value, "Could not decode client response")
        }
        return null
    }

    companion object {
        const val IDENTITY = "fetch-client"
    }
}

inline fun <reified O : @Serializable Any> fetchEngineTemplate(): FetchEngineTemplate<O, NoBody> =
    fetchEngineTemplateWithBody<O, NoBody>()

inline fun <reified O : @Serializable Any, reified B : @Serializable Any> fetchEngineTemplateWithBody():
    FetchEngineTemplate<O, B> =
    FetchEngineTemplate(AviatorServiceUtils.makeClazzDefinition<O>(), AviatorServiceUtils.makeClazzDefinition<B>())
