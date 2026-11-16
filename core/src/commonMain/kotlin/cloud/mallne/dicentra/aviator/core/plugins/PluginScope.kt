package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.json.JsonElement

/** Typed extension point for plugin snapshots owned by optional Aviator modules. */
class PluginScopeKey<T : Any>(val identity: String) {
    init { require(identity.isNotBlank()) }
}

interface PluginScopeProvider {
    val key: PluginScopeKey<*>
    suspend fun provide(locator: ServiceLocator, requested: Map<String, JsonElement>): PluginScopeSnapshot
}

/** Invocation-local typed value, accessed only through its matching key. */
interface PluginScopeSnapshot {
    fun <T : Any> get(key: PluginScopeKey<T>): T?
}

private class TypedPluginScopeSnapshot<T : Any>(
    private val key: PluginScopeKey<T>,
    private val value: T?,
) : PluginScopeSnapshot {
    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> get(key: PluginScopeKey<R>): R? =
        if (key === this.key) value as? R else null
}

class TypedPluginScopeProvider<T : Any>(
    override val key: PluginScopeKey<T>,
    private val provide: suspend (ServiceLocator, Map<String, JsonElement>) -> T?,
) : PluginScopeProvider {
    override suspend fun provide(locator: ServiceLocator, requested: Map<String, JsonElement>): PluginScopeSnapshot =
        TypedPluginScopeSnapshot(key, provide.invoke(locator, requested))
}
