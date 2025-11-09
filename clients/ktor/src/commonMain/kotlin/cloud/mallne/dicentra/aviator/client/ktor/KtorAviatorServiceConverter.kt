package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.core.APIToServiceConverter
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-pluginMaterialization`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-serviceDelegateCall`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-serviceOptions`
import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.BasicPluginActivationScope
import cloud.mallne.dicentra.aviator.exceptions.AviatorValidationException
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.routes
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.polyfill.ensure
import cloud.mallne.dicentra.polyfill.ensureNotNull
import io.ktor.client.*
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@OptIn(InternalAviatorAPI::class)
class KtorAviatorServiceConverter(
    val httpClient: HttpClient = HttpClient(),
    val serializers: MutableList<StringFormat> = mutableListOf(Json),
) : APIToServiceConverter {
    constructor(httpClient: HttpClient, json: Json = Json): this(httpClient, mutableListOf(json))

    fun swapPlugins(
        to: KtorAviatorService,
        plugins: AviatorPluginActivationScope.() -> Unit
    ): KtorAviatorService {
        val registry = crystallizePlugins(plugins)
        val pluginsForRoute =
            pluginsForRoute(registry, to.serviceLocator, to.route.`x-dicentra-aviator-pluginMaterialization` ?: mapOf())
        return to.copy(plugins = pluginsForRoute)
    }

    fun swapClient(
        to: KtorAviatorService,
        client: HttpClient = httpClient
    ): KtorAviatorService {
        return to.copy(client = client)
    }

    override fun build(
        api: OpenAPI,
        plugins: AviatorPluginActivationScope.() -> Unit
    ): List<KtorAviatorService> {
        val version = api.`x-dicentra-aviator`
        ensureNotNull(version) {
            AviatorValidationException("The given OpenAPI specification does not contain a Aviator Version Attribute at root Level.")
        }
        ensure(AviatorExtensionSpec.understandsVersions.contains(version)) {
            AviatorValidationException("This version of Aviator (${AviatorExtensionSpec.SpecVersion}) can't interpret the version of the given OpenAPI definition (${version}).")
        }
        val registry = crystallizePlugins(plugins)
        val routes = api.routes()
        val services = routes.mapNotNull {
            val l = it.`x-dicentra-aviator-serviceDelegateCall`
            val options = it.`x-dicentra-aviator-serviceOptions`
            val pluginsRequested = it.`x-dicentra-aviator-pluginMaterialization`
            if (l != null && options != null) {
                val locator = ServiceLocator(l)
                val pluginsForRoute = pluginsForRoute(registry, locator, pluginsRequested ?: mapOf())
                KtorAviatorService(
                    serviceLocator = locator,
                    options = options,
                    client = httpClient,
                    plugins = pluginsForRoute,
                    route = it,
                    oas = api,
                    serializers = serializers,
                )
            } else null
        }
        return services
    }
}