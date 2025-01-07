package cloud.mallne.dicentra.aviator.core.mock

import cloud.mallne.dicentra.aviator.core.APIToServiceConverter
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.execution.AviatorExtensionSpec
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.core.plugins.BasicPluginActivationScope
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.koas.typed.routes
import cloud.mallne.dicentra.aviator.model.ServiceLocator

object MockConverter : APIToServiceConverter {
    override fun build(
        api: OpenAPI,
        plugins: AviatorPluginActivationScope.() -> Unit
    ): Map<ServiceLocator, MockedAviatorService> {
        val version = AviatorExtensionSpec.Version.find(api)
        require(version != null) {
            "The given OpenAPI specification does not contain a Aviator Version Attribute at root Level."
        }
        require(AviatorExtensionSpec.understandsVersions.contains(version)) {
            "This version of Aviator (${AviatorExtensionSpec.SpecVersion}) can't interpret the version of the given OpenAPI definition (${version})."
        }
        val scope = BasicPluginActivationScope()
        plugins.invoke(scope)
        val routes = api.routes()
        val routing = mutableMapOf<ServiceLocator, Pair<Route, ServiceOptions>>()
        routes.forEach {
            val l = AviatorExtensionSpec.ServiceLocator.R.find(it)
            val options = AviatorExtensionSpec.ServiceOptions.R.findComplex(it)
            if (l != null && options != null) {
                routing[ServiceLocator(l)] = it to options
            }
        }
        val services = routing.map { (locator, route) ->
            val pluginsForRoute = scope.registry.filter { inst ->
                inst.configurationBundle.serviceFilter.isEmpty() || inst.configurationBundle.serviceFilter.map { it.toString() }
                    .contains(locator.toString())
            }

            val service = MockedAviatorService(
                serviceLocator = locator,
                options = route.second,
                plugins = pluginsForRoute,
                route = route.first,
                oas = api,
            )
            locator to service
        }.toMap()
        return services
    }
}