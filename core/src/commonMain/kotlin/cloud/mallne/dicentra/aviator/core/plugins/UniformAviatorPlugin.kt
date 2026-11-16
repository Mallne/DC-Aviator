package cloud.mallne.dicentra.aviator.core.plugins

/**
 * Install-time composite plugin definition. Installation bakes one config into an instance
 * exposed through engine, delegation, client-stage, and scope-provider roles.
 */
interface UniformAviatorPlugin<ConfigScope : AviatorPluginConfigScope> {
    val identity: String
    fun install(config: ConfigScope.() -> Unit = {}): UniformAviatorPluginInstance
}

interface UniformAviatorPluginInstance :
    EngineAviatorPluginInstance,
    DelegationAviatorPluginInstance,
    AviatorPluginInstance,
    PluginScopeProvider {
    override fun requestReconfigure(
        oasConfig: kotlinx.serialization.json.JsonElement,
    ): UniformAviatorPluginInstance = this
}
