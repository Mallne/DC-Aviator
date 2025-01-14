package cloud.mallne.dicentra.aviator.ktor

import cloud.mallne.dicentra.aviator.core.APIToServiceConverter
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-serviceDelegateCall`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-serviceOptions`
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.core.plugins.BasicPluginActivationScope
import cloud.mallne.dicentra.aviator.exceptions.AviatorValidationException
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.koas.typed.routes
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.polyfill.ensure
import cloud.mallne.dicentra.polyfill.ensureNotNull
import io.ktor.client.*
import kotlinx.serialization.json.Json

class KtorAviatorServiceConverter(
    val httpClient: HttpClient = HttpClient(),
    val json: Json = Json
) : APIToServiceConverter {
    override fun build(
        api: OpenAPI,
        plugins: AviatorPluginActivationScope.() -> Unit
    ): Map<ServiceLocator, KtorAviatorService> {
        val version = api.`x-dicentra-aviator`
        ensureNotNull(version) {
            AviatorValidationException("The given OpenAPI specification does not contain a Aviator Version Attribute at root Level.")
        }
        ensure(AviatorExtensionSpec.understandsVersions.contains(version)) {
            AviatorValidationException("This version of Aviator (${AviatorExtensionSpec.SpecVersion}) can't interpret the version of the given OpenAPI definition (${version}).")
        }
        val scope = BasicPluginActivationScope()
        plugins.invoke(scope)
        val routes = api.routes()
        val routing = mutableMapOf<ServiceLocator, Pair<Route, ServiceOptions>>()
        routes.forEach {
            val l = it.`x-dicentra-aviator-serviceDelegateCall`
            val options = it.`x-dicentra-aviator-serviceOptions`
            if (l != null && options != null) {
                routing[ServiceLocator(l)] = it to options
            }
        }
        val services = routing.map { (locator, route) ->
            val pluginsForRoute = scope.registry.filter { inst ->
                inst.configurationBundle.serviceFilter.isEmpty() || inst.configurationBundle.serviceFilter.map { it.toString() }
                    .contains(locator.toString())
            }

            val service = KtorAviatorService(
                serviceLocator = locator,
                options = route.second,
                client = httpClient,
                plugins = pluginsForRoute,
                route = route.first,
                oas = api,
                json = json,
            )
            locator to service
        }.toMap()
        return services
    }
}