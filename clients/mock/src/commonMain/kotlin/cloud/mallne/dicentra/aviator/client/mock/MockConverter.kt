package cloud.mallne.dicentra.aviator.client.mock

import cloud.mallne.dicentra.aviator.core.APIToServiceConverter
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-pluginMaterialization`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-serviceDelegateCall`
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-serviceOptions`
import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.io.adapter.CommonAdapter
import cloud.mallne.dicentra.aviator.core.plugins.AviatorAdapterPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.exceptions.AviatorValidationException
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.routes
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.polyfill.ensure
import cloud.mallne.dicentra.polyfill.ensureNotNull
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.json.Json

@OptIn(InternalAviatorAPI::class)
class MockConverter(
    val serializers: MutableList<SerialFormat> = mutableListOf(Json),
) : APIToServiceConverter {
    override fun build(
        api: OpenAPI,
        plugins: AviatorPluginActivationScope.() -> Unit,
    ): List<MockedAviatorService> {
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
                val adapters = pluginsForRoute.mapNotNull { (it as? AviatorAdapterPluginInstance)?.adapters }.flatten()
                val deserializers =
                    pluginsForRoute.mapNotNull { (it as? AviatorAdapterPluginInstance)?.deserializers }.flatten()
                MockedAviatorService(
                    serviceLocator = locator,
                    options = options,
                    plugins = pluginsForRoute,
                    route = it,
                    oas = api,
                    serializers = serializers,
                    adapters = adapters + CommonAdapter.adapters,
                    deserializers = deserializers + CommonAdapter.deserializers,
                )
            } else null
        }
        return services
    }


}