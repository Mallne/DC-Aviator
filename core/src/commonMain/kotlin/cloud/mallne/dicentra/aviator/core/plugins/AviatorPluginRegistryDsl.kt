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

    fun <ConfigScope : AviatorPluginConfigScope> plugin(
        plugin: UniformAviatorPlugin<ConfigScope>,
        activation: PluginActivation? = null,
        config: ConfigScope.() -> Unit = {},
    ) {
        val instance = plugin.install(config)
        uniformPlugins += UniformPluginRegistration(instance, activation ?: defaultClientActivation(instance))
    }

    fun <ConfigScope : AviatorPluginConfigScope> clientPlugin(
        plugin: AviatorPlugin<ConfigScope>,
        activation: PluginActivation? = null,
        config: ConfigScope.() -> Unit = {},
    ) {
        val instance = plugin.install(config)
        clientPlugin(instance, activation ?: defaultClientActivation(instance))
    }

    fun clientPlugin(
        instance: AviatorPluginInstance,
        activation: PluginActivation = defaultClientActivation(instance),
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
