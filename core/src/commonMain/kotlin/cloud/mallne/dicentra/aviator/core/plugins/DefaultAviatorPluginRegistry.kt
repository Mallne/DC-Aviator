package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.json.JsonElement

class DefaultAviatorPluginRegistry(
    engine: List<EnginePluginRegistration> = emptyList(),
    delegation: List<DelegationPluginRegistration> = emptyList(),
    uniform: List<UniformPluginRegistration> = emptyList(),
    clients: List<ClientPluginRegistration> = emptyList(),
    scopeProviders: List<PluginScopeProvider> = emptyList(),
) : AviatorPluginRegistry {
    // Freeze collection membership at installation; plugin instances themselves are immutable
    // snapshots by contract and DEFINITION activation must return a reconfigured instance.
    private val installedEngine = engine.toList()
    private val installedDelegation = delegation.toList()
    private val installedUniform = uniform.toList()
    private val installedClients = clients.toList()
    private val installedScopeProviders = scopeProviders.toList()

    init {
        val uniformScopes = installedUniform.map {
            it.instance.identity to it.instance.configurationBundle.serviceFilter
        }
        requireDisjointScopes(
            installedEngine.map { it.instance.identity to it.instance.configurationBundle.serviceFilter } +
                uniformScopes,
            "Engine plugin",
        )
        requireDisjointScopes(
            installedDelegation.map { it.instance.identity to it.instance.configurationBundle.serviceFilter } +
                uniformScopes,
            "Delegation plugin",
        )
        requireDisjointScopes(
            installedClients.map { it.instance.identity to it.instance.configurationBundle.serviceFilter } +
                uniformScopes,
            "Client plugin",
        )
        requireDisjointScopes(
            installedScopeProviders.map { it.key.identity to emptyList<ServiceLocator>() } +
                installedUniform.map { it.instance.key.identity to it.instance.configurationBundle.serviceFilter },
            "Plugin scope",
        )
    }

    override suspend fun invocationPlugins(
        locator: ServiceLocator,
        requested: Map<String, JsonElement>,
    ): AviatorPluginSnapshot {
        val uniformPlugins = installedUniform.mapNotNull { registration ->
            registration.takeIf { appliesTo(it.instance.configurationBundle, locator) }
                ?.let { selectedUniformPlugin(it, requested) }
        }
        val enginePlugins = buildList {
            for (registration in installedEngine) {
                val instance = registration.instance
                if (!appliesTo(instance.configurationBundle, locator)) continue
                when (registration.activation) {
                    PluginActivation.PASSIVE -> add(instance)
                    PluginActivation.DEFINITION ->
                        requested[instance.identity]?.let(instance::requestReconfigure)?.let(::add)
                }
            }
            addAll(uniformPlugins)
        }
        val delegationPlugins = buildList {
            for (registration in installedDelegation) {
                val instance = registration.instance
                if (!appliesTo(instance.configurationBundle, locator)) continue
                when (registration.activation) {
                    PluginActivation.PASSIVE -> add(instance)
                    PluginActivation.DEFINITION ->
                        requested[instance.identity]?.let(instance::requestReconfigure)?.let(::add)
                }
            }
            addAll(uniformPlugins)
        }
        val clientPlugins = buildList {
            for (registration in installedClients) {
                val instance = registration.instance
                if (instance.configurationBundle.serviceFilter.isNotEmpty() &&
                    locator !in instance.configurationBundle.serviceFilter
                ) continue
                when (registration.activation) {
                    PluginActivation.PASSIVE -> add(instance)
                    PluginActivation.DEFINITION ->
                        requested[instance.identity]?.let(instance::requestReconfigure)?.let(::add)
                }
            }
            addAll(uniformPlugins)
        }
        val scopes = buildList {
            for (provider in installedScopeProviders) add(provider.provide(locator, requested))
            for (plugin in uniformPlugins) add(plugin.provide(locator, requested))
        }
        return AviatorPluginSnapshot(
            enginePlugins = enginePlugins,
            delegationPlugins = delegationPlugins,
            clientPlugins = clientPlugins,
            scopes = scopes,
        )
    }

    private fun appliesTo(configuration: AviatorPluginConfigScope, locator: ServiceLocator): Boolean =
        configuration.serviceFilter.let { it.isEmpty() || locator in it }

    private fun selectedUniformPlugin(
        registration: UniformPluginRegistration,
        requested: Map<String, JsonElement>,
    ): UniformAviatorPluginInstance? = when (registration.activation) {
        PluginActivation.PASSIVE -> registration.instance
        PluginActivation.DEFINITION ->
            requested[registration.instance.identity]?.let(registration.instance::requestReconfigure)
    }

    private fun requireDisjointScopes(
        registrations: List<Pair<String, List<ServiceLocator>>>,
        role: String,
    ) {
        require(registrations.all { it.first.isNotBlank() }) { "$role identities must not be blank" }
        for (leftIndex in registrations.indices) {
            val (leftIdentity, leftLocators) = registrations[leftIndex]
            for (rightIndex in leftIndex + 1 until registrations.size) {
                val (rightIdentity, rightLocators) = registrations[rightIndex]
                if (leftIdentity != rightIdentity) continue
                val overlaps = leftLocators.isEmpty() || rightLocators.isEmpty() ||
                    leftLocators.any { it in rightLocators }
                require(!overlaps) {
                    "$role '$leftIdentity' has overlapping ServiceLocator scopes"
                }
            }
        }
    }
}
