package cloud.mallne.dicentra.aviator.core.plugins

@AviatorPluginDsl
class AviatorPluginRegistryBuilder {
    private val enginePlugins = mutableListOf<EnginePluginRegistration>()
    private val delegationPlugins = mutableListOf<DelegationPluginRegistration>()
    private val uniformPlugins = mutableListOf<UniformPluginRegistration>()
    private val clientPlugins = mutableListOf<ClientPluginRegistration>()
    private val scopeProviders = mutableListOf<PluginScopeProvider>()

    fun <ConfigScope : AviatorPluginConfigScope> enginePlugin(
        plugin: EngineAviatorPlugin<ConfigScope>,
        activation: PluginActivation = PluginActivation.DEFINITION,
        config: ConfigScope.() -> Unit = {},
    ) {
        enginePlugins += EnginePluginRegistration(plugin.install(config), activation)
    }

    fun <ConfigScope : AviatorPluginConfigScope> delegationPlugin(
        plugin: DelegationAviatorPlugin<ConfigScope>,
        activation: PluginActivation = PluginActivation.DEFINITION,
        config: ConfigScope.() -> Unit = {},
    ) {
        delegationPlugins += DelegationPluginRegistration(plugin.install(config), activation)
    }

    fun <ConfigScope : AviatorPluginConfigScope> uniformPlugin(
        plugin: UniformAviatorPlugin<ConfigScope>,
        activation: PluginActivation = PluginActivation.DEFINITION,
        config: ConfigScope.() -> Unit = {},
    ) {
        uniformPlugins += UniformPluginRegistration(plugin.install(config), activation)
    }

    fun clientPlugin(
        instance: AviatorPluginInstance,
        activation: PluginActivation = PluginActivation.DEFINITION,
    ) {
        clientPlugins += ClientPluginRegistration(instance, activation)
    }

    fun scopeProvider(provider: PluginScopeProvider) {
        scopeProviders += provider
    }

    internal fun build(): AviatorPluginRegistry = DefaultAviatorPluginRegistry(
        engine = enginePlugins,
        delegation = delegationPlugins,
        uniform = uniformPlugins,
        clients = clientPlugins,
        scopeProviders = scopeProviders,
    )
}

fun aviatorPluginRegistry(
    configure: AviatorPluginRegistryBuilder.() -> Unit = {},
): AviatorPluginRegistry = AviatorPluginRegistryBuilder().apply(configure).build()
