package cloud.mallne.dicentra.aviator.client.fetch

import cloud.mallne.dicentra.aviator.core.*
import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-pluginMaterialization`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-serviceDelegateCall`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-serviceOptions`
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.adapter.CommonAdapter
import cloud.mallne.dicentra.aviator.core.plugins.AviatorAdapterPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.exceptions.AviatorValidationException
import cloud.mallne.dicentra.aviator.koas.typed.routes
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.polyfill.ensure
import cloud.mallne.dicentra.polyfill.ensureNotNull
import io.ktor.openapi.OpenApiDoc
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.json.Json

@OptIn(InternalAviatorAPI::class)
/**
 * Builds Aviator services whose HTTP transport uses the JavaScript Fetch API.
 *
 * By default, requests use `globalThis.fetch`, which is available in modern browsers and
 * Node.js. Supply a Fetch-compatible function to use a framework-managed transport instead.
 * This is useful for SvelteKit server-side requests, where `event.fetch` can provide request
 * context, cookies, and framework-specific request handling:
 *
 * ```kotlin
 * val converter = FetchAviatorServiceConverter(fetcher = event.fetch)
 * ```
 *
 * The injected function receives the resolved URL and a standard Fetch `RequestInit`-shaped
 * object, and must return a promise for a response with `status`, `headers`, and `arrayBuffer()`.
 *
 * @param fetcher Fetch-compatible JavaScript function. Defaults to `globalThis.fetch`.
 * @param serializers serialization formats available to response adapters. JSON is registered
 *   by default.
 * @param maxResponseBytes maximum response body size, in bytes, accepted from the transport.
 */
class FetchAviatorServiceConverter(
    private val fetcher: dynamic = defaultFetch(),
    private val serializers: List<SerialFormat> = listOf(Json),
    private val maxResponseBytes: Int = 16_777_216,
) : APIToServiceConverter {
    init {
        require(maxResponseBytes > 0) { "maxResponseBytes must be positive" }
    }

    override fun build(
        api: OpenApiDoc,
        plugins: AviatorPluginActivationScope.() -> Unit,
    ): List<AviatorServiceDataHolder> {
        val version = api.`x-dicentra-aviator`
        ensureNotNull(version) {
            AviatorValidationException("The given OpenAPI specification does not contain an Aviator Version Attribute at root Level.")
        }
        ensure(AviatorExtensionSpec.understandsVersions.contains(version)) {
            AviatorValidationException("This Aviator version can't interpret the given OpenAPI definition ($version).")
        }
        val registry = crystallizePlugins(plugins)
        return api.routes().mapNotNull { route ->
            val locatorValue = route.`x-dicentra-aviator-serviceDelegateCall` ?: return@mapNotNull null
            val locator = ServiceLocator(locatorValue)
            val requested = route.`x-dicentra-aviator-pluginMaterialization` ?: emptyMap()
            val installed = pluginsForRoute(registry, locator, requested)
            val adapters = installed.filterIsInstance<AviatorAdapterPluginInstance>().flatMap { it.adapters }
            val deserializers = installed.filterIsInstance<AviatorAdapterPluginInstance>().flatMap { it.deserializers }
            FetchAviatorService(
                serviceLocator = locator,
                options = route.`x-dicentra-aviator-serviceOptions` ?: ServiceOptions.EmptyObject,
                plugins = installed,
                route = route,
                oas = api,
                adapters = adapters + CommonAdapter.adapters,
                deserializers = deserializers + CommonAdapter.deserializers,
                fetcher = fetcher,
                serializers = serializers,
                maxResponseBytes = maxResponseBytes,
            )
        }
    }
}
