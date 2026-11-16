package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.adapter.CommonAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.request.RequestBodyAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.response.ResponseBodyAdapter

data class EngineInvocationContext(
    val clientPlugins: List<AviatorPluginInstance>,
    private val scopes: List<PluginScopeSnapshot> = emptyList(),
) {
    fun <T : Any> scope(key: PluginScopeKey<T>): T? = scopes.firstNotNullOfOrNull { it.get(key) }

    fun clientPluginsOver(installed: List<AviatorPluginInstance>): EngineClientPlugins {
        val invocationIdentities = clientPlugins.mapTo(mutableSetOf()) { it.identity }
        val plugins = installed.filterNot { it.identity in invocationIdentities } + clientPlugins
        val adapters = plugins.filterIsInstance<AviatorAdapterPluginInstance>()
        return EngineClientPlugins(
            plugins = plugins,
            adapters = adapters.flatMap { it.adapters } + CommonAdapter.adapters,
            deserializers = adapters.flatMap { it.deserializers } + CommonAdapter.deserializers,
        )
    }
}

data class EngineClientPlugins(
    val plugins: List<AviatorPluginInstance>,
    val adapters: List<RequestBodyAdapter<out NetworkBody>>,
    val deserializers: List<ResponseBodyAdapter>,
)
