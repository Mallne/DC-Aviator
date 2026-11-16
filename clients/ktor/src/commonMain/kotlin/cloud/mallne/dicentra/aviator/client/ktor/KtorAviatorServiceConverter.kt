package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.core.APIToServiceConverter
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-pluginMaterialization`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-serviceDelegateCall`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-serviceOptions`
import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.io.adapter.CommonAdapter
import cloud.mallne.dicentra.aviator.core.plugins.AviatorAdapterPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.exceptions.AviatorValidationException
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.koas.typed.routes
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.polyfill.ensure
import cloud.mallne.dicentra.polyfill.ensureNotNull
import io.ktor.client.*
import io.ktor.client.plugins.HttpSend
import io.ktor.openapi.*
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@OptIn(InternalAviatorAPI::class)
class KtorAviatorServiceConverter(
    httpClient: HttpClient = HttpClient(),
    val serializers: List<SerialFormat> = listOf(Json),
    val maxResponseBytes: Long = 16_777_216,
) : APIToServiceConverter {
    /** Prevent transport-managed redirects/retries from bypassing Aviator's per-attempt control. */
    val httpClient: HttpClient = httpClient.config {
        followRedirects = false
        installOrReplace(HttpSend) { maxSendCount = 1 }
    }

    constructor(httpClient: HttpClient, json: Json = Json, maxResponseBytes: Long = 16_777_216) :
        this(httpClient, mutableListOf(json), maxResponseBytes)

    init {
        require(maxResponseBytes in 1 until Int.MAX_VALUE.toLong()) { "maxResponseBytes must be between 1 and Int.MAX_VALUE - 1" }
    }

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
        return to.copy(client = client.config {
            followRedirects = false
            installOrReplace(HttpSend) { maxSendCount = 1 }
        })
    }

    override fun build(
        api: OpenApiDoc,
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
        val services = routes.mapNotNull { route ->
            val l = route.`x-dicentra-aviator-serviceDelegateCall`
            val options = route.`x-dicentra-aviator-serviceOptions` ?: ServiceOptions.EmptyObject
            val pluginsRequested = route.`x-dicentra-aviator-pluginMaterialization`
            if (l != null) {
                val locator = ServiceLocator(l)
                materialize(api, route, locator, registry, options, pluginsRequested ?: emptyMap())
            } else null
        }
        return services
    }

    /** Materialize an operation selected by a workflow using the action's locator. */
    fun buildOperation(
        api: OpenApiDoc,
        route: Route,
        locator: ServiceLocator,
        plugins: AviatorPluginActivationScope.() -> Unit = {},
    ): KtorAviatorService {
        require(api.routes().any { it == route }) { "Route does not belong to the supplied OpenAPI document" }
        return materialize(
            api, route, locator, crystallizePlugins(plugins),
            route.`x-dicentra-aviator-serviceOptions` ?: ServiceOptions.EmptyObject,
            route.`x-dicentra-aviator-pluginMaterialization` ?: emptyMap(),
        )
    }

    private fun materialize(
        api: OpenApiDoc,
        route: Route,
        locator: ServiceLocator,
        registry: List<AviatorPluginInstance>,
        options: ServiceOptions,
        requested: Map<String, JsonElement>,
    ): KtorAviatorService {
        val selected = pluginsForRoute(registry, locator, requested)
        return KtorAviatorService(
            serviceLocator = locator,
            options = options,
            client = httpClient,
            plugins = selected,
            route = route,
            oas = api,
            adapters = selected.mapNotNull { (it as? AviatorAdapterPluginInstance)?.adapters }.flatten() + CommonAdapter.adapters,
            deserializers = selected.mapNotNull { (it as? AviatorAdapterPluginInstance)?.deserializers }.flatten() + CommonAdapter.deserializers,
            serializers = serializers,
            maxResponseBytes = maxResponseBytes,
        )
    }
}
