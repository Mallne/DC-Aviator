package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.BasicPluginActivationScope
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.json.JsonElement
import kotlin.collections.component1
import kotlin.collections.component2

interface APIToServiceConverter {
    fun build(
        api: OpenAPI,
        plugins: AviatorPluginActivationScope.() -> Unit = {}
    ): List<AviatorServiceDataHolder>

    @InternalAviatorAPI
    fun crystallizePlugins(plugins: AviatorPluginActivationScope.() -> Unit): List<AviatorPluginInstance> {
        val scope = BasicPluginActivationScope()
        plugins.invoke(scope)
        return scope.registry
    }

    @InternalAviatorAPI
    fun pluginsForRoute(
        registry: List<AviatorPluginInstance>,
        locator: ServiceLocator,
        requestedByOas: Map<String, JsonElement>
    ): List<AviatorPluginInstance> {
        val pluginsForRoute = registry.filter { inst ->
            inst.configurationBundle.serviceFilter.isEmpty()
                    || inst.configurationBundle.serviceFilter.map { it.toString() }.contains(locator.toString())
        }
        val reconfigured = requestedByOas.mapNotNull { (identity, pluginConfig) ->
            pluginsForRoute.find { it.identity == identity }?.requestReconfigure(pluginConfig)
        }
        return pluginsForRoute.map { current ->
            val reconf = reconfigured.find { current.identity == it.identity }
            reconf ?: current
        }
    }
}