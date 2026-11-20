package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.core.engine.EngineTemplate
import cloud.mallne.dicentra.aviator.core.engine.AviatorEngine
import cloud.mallne.dicentra.aviator.core.engine.InvocationMode
import cloud.mallne.dicentra.aviator.core.engine.InvocationOutcome
import cloud.mallne.dicentra.aviator.core.plugins.EngineInvocationContext
import cloud.mallne.dicentra.aviator.core.engine.OpenApiClientInput
import cloud.mallne.dicentra.aviator.core.engine.EngineDefinitionConverter
import cloud.mallne.dicentra.aviator.core.engine.ExecutableDefinition
import cloud.mallne.dicentra.aviator.core.engine.OpenApiOperationDefinitionRequest
import cloud.mallne.dicentra.aviator.core.NoBody
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-pluginMaterialization`
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import io.ktor.openapi.OpenApiDoc
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType

/** A materialized OpenAPI operation owned by the Ktor client delegation. */
class KtorClientDefinition(val service: KtorAviatorService) : ExecutableDefinition {
    override val locator: ServiceLocator get() = service.serviceLocator
    override val delegation: String get() = KtorEngineTemplate.IDENTITY
    override val outboundTargets: List<String> get() = service.oas.servers.orEmpty().map { it.url }
    override val pluginConfiguration: Map<String, JsonElement> =
        service.route.`x-dicentra-aviator-pluginMaterialization`.orEmpty().toMap()
}

class KtorEngineDefinitionConverter(
    private val converter: KtorAviatorServiceConverter,
) : EngineDefinitionConverter<KtorClientDefinition> {
    /** Convert the operations that declare Aviator locators in the document. */
    override fun convert(
        api: OpenApiDoc,
        plugins: AviatorPluginActivationScope.() -> Unit,
    ): List<KtorClientDefinition> = converter.build(api, plugins).map(::KtorClientDefinition)

    fun convert(source: OpenApiOperationDefinitionRequest): KtorClientDefinition =
        KtorClientDefinition(
            converter.buildOperation(source.api, source.route, source.locator, source.activatePlugins),
        )
}

/** Preserves the HTTP status when an invocation fails. */
class KtorClientInvocationException(
    val statusCode: Int?,
    message: String,
) : Exception(message)

class KtorEngineTemplate<O : @Serializable Any, B : @Serializable Any>(
    private val outputClazz: Triple<KClass<O>, KType, KSerializer<O>>,
    private val bodyClazz: Triple<KClass<B>, KType, KSerializer<B>>,
) : EngineTemplate<OpenApiClientInput<B>, KtorExecutionContext<O, B>> {
    override val identity: String = IDENTITY

    override suspend fun execute(
        definition: ExecutableDefinition,
        input: OpenApiClientInput<B>,
        context: EngineInvocationContext,
    ): Result<KtorExecutionContext<O, B>> {
        require(definition is KtorClientDefinition) { "Expected a Ktor client definition" }
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

    override fun classifyFailure(output: KtorExecutionContext<O, B>): Exception? {
        val response = output.networkChain.firstOrNull { it.response?.status?.value in 200..299 }?.response
        if (response == null) {
            val status = output.networkChain.firstNotNullOfOrNull { it.response?.status?.value }
            return KtorClientInvocationException(status, "No successful client response")
        }
        if (response.content?.isNotEmpty() == true && output.result == null) {
            return KtorClientInvocationException(response.status.value, "Could not decode client response")
        }
        return null
    }

    companion object {
        const val IDENTITY = "ktor-client"
    }
}

inline fun <reified O : @Serializable Any> ktorEngineTemplate(): KtorEngineTemplate<O, NoBody> =
    ktorEngineTemplateWithBody<O, NoBody>()

/** Invoke a bodyless OpenAPI operation using the standard Ktor template for [O]. */
suspend inline fun <reified O : @Serializable Any> AviatorEngine.invoke(
    locator: ServiceLocator,
    input: OpenApiClientInput<NoBody> = OpenApiClientInput(),
    mode: InvocationMode = InvocationMode.FIRST,
): List<InvocationOutcome<KtorExecutionContext<O, NoBody>>> =
    invoke(locator, ktorEngineTemplate<O>(), input, mode)

inline fun <reified O : @Serializable Any, reified B : @Serializable Any> ktorEngineTemplateWithBody():
    KtorEngineTemplate<O, B> =
    KtorEngineTemplate(AviatorServiceUtils.makeClazzDefinition<O>(), AviatorServiceUtils.makeClazzDefinition<B>())
