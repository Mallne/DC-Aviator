package cloud.mallne.dicentra.aviator.ktor

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.InflatedServiceOptions
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionPipeline
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.koas.typed.TemplateParser.parsePath
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import io.ktor.client.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.typeOf

class KtorAviatorService(
    override val serviceLocator: ServiceLocator,
    override val options: ServiceOptions,
    val client: HttpClient,
    override val plugins: List<AviatorPluginInstance>,
    override val route: Route,
    override val oas: OpenAPI,
    override val json: Json
) : AviatorServiceDataHolder {

    init {
        AviatorServiceUtils.validate(this)
    }

    inline fun <reified T : InflatedServiceOptions> optionBundle(): T = AviatorServiceUtils.optionBundle(options)

    suspend inline fun <reified O : @Serializable Any, reified B : @Serializable Any> request(
        requestBody: B? = null,
        useSerializer: KSerializer<O> = serializer(),
        options: Map<String, @Serializable Any> = emptyMap(),
        requestParams: Map<String, List<String>> = emptyMap()
    ): O? {
        val executor = KtorStagedExecutor<O, B>()
        val pipeline = AviatorExecutionPipeline(
            context = KtorExecutionContext(
                dataHolder = this,
                outputClazz = Triple(O::class, typeOf<O>(), serializer<O>()),
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
        return ctx.result
    }

    fun close() = client.close()

    fun catchPaths(requestParams: Map<String, List<String>>): List<String> {
        val pathSlug = route.parsePath(requestParams)
        val serverSlugs = oas.servers.map { "${it.parsePath(requestParams)}$pathSlug" }
        return serverSlugs
    }
}